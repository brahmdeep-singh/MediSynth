import { appointments as seedAppointments } from "../data/appointments";
import { doctors } from "../data/doctors";
import { simulateRequest, http, USE_MOCKS } from "./api";

// Kept in module memory so create/cancel/reschedule persist across navigation
// during a session, without needing a real backend yet.
let appointments = [...seedAppointments];

function mock_getAppointments({ patientId, doctorId } = {}) {
  let list = appointments;
  if (patientId) list = list.filter((a) => a.patientId === patientId);
  if (doctorId) list = list.filter((a) => a.doctorId === doctorId);
  return simulateRequest(list);
}

function mock_getDoctors() {
  return simulateRequest(doctors);
}

function mock_getAppointmentById(id) {
  return simulateRequest(appointments.find((a) => a.id === id) || null);
}

function mock_completeAppointment(id) {
  appointments = appointments.map((a) => (a.id === id ? { ...a, status: "completed" } : a));
  return simulateRequest(true, { delay: 400 });
}

function mock_checkSlotAvailability(doctorId, date, timeSlot) {
  const clash = appointments.some(
    (a) => a.doctorId === doctorId && a.date === date && a.timeSlot === timeSlot && a.status !== "cancelled"
  );
  return simulateRequest(!clash);
}

function mock_bookAppointment({ patientId, patientName, doctorId, date, timeSlot }) {
  const doctor = doctors.find((d) => d.id === doctorId);
  const newAppt = {
    id: `A-${Math.floor(30000 + Math.random() * 9000)}`,
    patientId,
    patientName,
    doctorId,
    doctorName: doctor ? doctor.name : "Unknown",
    date,
    timeSlot,
    status: "pending",
  };
  appointments = [newAppt, ...appointments];
  return simulateRequest(newAppt, { delay: 600 });
}

function mock_cancelAppointment(id) {
  appointments = appointments.map((a) => (a.id === id ? { ...a, status: "cancelled" } : a));
  return simulateRequest(true, { delay: 400 });
}

function mock_rescheduleAppointment(id, { date, timeSlot }) {
  appointments = appointments.map((a) =>
    a.id === id ? { ...a, date, timeSlot, status: "pending" } : a
  );
  return simulateRequest(true, { delay: 400 });
}


// ===================================================================== real backend
// The backend stores a single UTC instant (appointmentTime). The UI works with a
// local date + "10:30 AM" label, so we convert in both directions here.

function labelToHm(label) {
  const m = /^(\d{1,2}):(\d{2}) (AM|PM)$/.exec(label);
  if (!m) return null;
  let h = Number(m[1]) % 12;
  if (m[3] === "PM") h += 12;
  return [h, Number(m[2])];
}

function toIso(date, timeSlot) {
  const hm = labelToHm(timeSlot);
  if (!date || !hm) throw new Error("Choose a valid date and time.");
  const [y, mo, d] = date.split("-").map(Number);
  return new Date(y, mo - 1, d, hm[0], hm[1], 0, 0).toISOString(); // local wall-clock -> UTC instant
}

function fromApi(a) {
  const dt = new Date(a.appointmentTime);
  const pad = (n) => String(n).padStart(2, "0");
  return {
    id: a.id,
    patientId: a.patientId,
    patientName: a.patientName,
    doctorId: a.doctorId,
    doctorName: a.doctorName,
    specialization: a.specialization,
    reason: a.reason,
    date: `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}`,
    timeSlot: dt.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit", hour12: true }),
    startTime: a.appointmentTime,
    status: String(a.status).toLowerCase(),
  };
}

const real = {
  async getAppointments() {
    // /my returns the caller's own appointments (patient or doctor); the server decides which.
    const page = await http("/api/appointments/my", { query: { size: 100 } });
    return page.content.map(fromApi);
  },
  async getDoctors() {
    const page = await http("/api/doctors", { query: { size: 100 } });
    return page.content; // { id (doctor profile id), name, specialization, ... }
  },
  async getAppointmentById(id) {
    try { return fromApi(await http(`/api/appointments/${id}`)); }
    catch (e) { if (e.status === 404) return null; throw e; }
  },
  async completeAppointment(id) {
    await http(`/api/appointments/${id}/status`, { method: "PATCH", body: { status: "COMPLETED" } });
    return true;
  },
  async confirmAppointment(id) {
    await http(`/api/appointments/${id}/status`, { method: "PATCH", body: { status: "CONFIRMED" } });
    return true;
  },
  // No availability endpoint exists: the database enforces slots, so a clash is reported on booking (409).
  async checkSlotAvailability() { return true; },
  async bookAppointment({ doctorId, date, timeSlot, reason }) {
    const a = await http("/api/appointments", {
      method: "POST",
      body: { doctorId: Number(doctorId), appointmentTime: toIso(date, timeSlot), reason: (reason || "").trim() },
    });
    return fromApi(a);
  },
  async cancelAppointment(id) {
    await http(`/api/appointments/${id}/cancel`, { method: "POST", body: {} });
    return true;
  },
  async rescheduleAppointment() { throw new Error("Rescheduling is not supported by the backend yet. Cancel and book again."); },
};

const mockConfirm = (id) => {
  appointments = appointments.map((a) => (a.id === id ? { ...a, status: "confirmed" } : a));
  return simulateRequest(true, { delay: 300 });
};

const mock = {
  getAppointments: mock_getAppointments, getDoctors: mock_getDoctors, getAppointmentById: mock_getAppointmentById,
  completeAppointment: mock_completeAppointment, confirmAppointment: mockConfirm,
  checkSlotAvailability: mock_checkSlotAvailability, bookAppointment: mock_bookAppointment,
  cancelAppointment: mock_cancelAppointment, rescheduleAppointment: mock_rescheduleAppointment,
};
const impl = USE_MOCKS ? mock : real;

export const getAppointments = (...a) => impl.getAppointments(...a);
export const getDoctors = (...a) => impl.getDoctors(...a);
export const getAppointmentById = (...a) => impl.getAppointmentById(...a);
export const completeAppointment = (...a) => impl.completeAppointment(...a);
export const confirmAppointment = (...a) => impl.confirmAppointment(...a);
export const checkSlotAvailability = (...a) => impl.checkSlotAvailability(...a);
export const bookAppointment = (...a) => impl.bookAppointment(...a);
export const cancelAppointment = (...a) => impl.cancelAppointment(...a);
export const rescheduleAppointment = (...a) => impl.rescheduleAppointment(...a);
