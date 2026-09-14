import { appointments as seedAppointments } from "../data/appointments";
import { doctors } from "../data/doctors";
import { simulateRequest } from "./api";

// Kept in module memory so create/cancel/reschedule persist across navigation
// during a session, without needing a real backend yet.
let appointments = [...seedAppointments];

export function getAppointments({ patientId, doctorId } = {}) {
  let list = appointments;
  if (patientId) list = list.filter((a) => a.patientId === patientId);
  if (doctorId) list = list.filter((a) => a.doctorId === doctorId);
  return simulateRequest(list);
}

export function getDoctors() {
  return simulateRequest(doctors);
}

export function getAppointmentById(id) {
  return simulateRequest(appointments.find((a) => a.id === id) || null);
}

export function completeAppointment(id) {
  appointments = appointments.map((a) => (a.id === id ? { ...a, status: "completed" } : a));
  return simulateRequest(true, { delay: 400 });
}

export function checkSlotAvailability(doctorId, date, timeSlot) {
  const clash = appointments.some(
    (a) => a.doctorId === doctorId && a.date === date && a.timeSlot === timeSlot && a.status !== "cancelled"
  );
  return simulateRequest(!clash);
}

export function bookAppointment({ patientId, patientName, doctorId, date, timeSlot }) {
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

export function cancelAppointment(id) {
  appointments = appointments.map((a) => (a.id === id ? { ...a, status: "cancelled" } : a));
  return simulateRequest(true, { delay: 400 });
}

export function rescheduleAppointment(id, { date, timeSlot }) {
  appointments = appointments.map((a) =>
    a.id === id ? { ...a, date, timeSlot, status: "pending" } : a
  );
  return simulateRequest(true, { delay: 400 });
}
