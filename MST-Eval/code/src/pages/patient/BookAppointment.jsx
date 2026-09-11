import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CheckCircle2, Check } from "lucide-react";
import Card from "../../components/common/Card";
import Button from "../../components/common/Button";
import FormField from "../../components/common/FormField";
import Select from "../../components/common/Select";
import Input from "../../components/common/Input";
import Alert from "../../components/common/Alert";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import * as appointmentService from "../../services/appointmentService";

// Mirrors Sequence Diagram 1 (Book Appointment) and the Activity Diagram's
// "search/select doctor -> book appointment -> appointment confirmation" flow.
const STEPS = ["Select doctor", "Choose date & time", "Confirm"];
const TIME_SLOTS = ["09:00 AM", "10:30 AM", "11:15 AM", "02:00 PM", "03:30 PM", "04:30 PM"];

export default function BookAppointment() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [doctors, setDoctors] = useState([]);
  const [doctorId, setDoctorId] = useState("");
  const [date, setDate] = useState("");
  const [timeSlot, setTimeSlot] = useState("");
  const [checking, setChecking] = useState(false);
  const [slotError, setSlotError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [confirmed, setConfirmed] = useState(null);

  useEffect(() => {
    appointmentService.getDoctors().then(setDoctors);
  }, []);

  const selectedDoctor = doctors.find((d) => d.id === doctorId);

  async function handleNextFromStep1() {
    if (!doctorId) return;
    setStep(1);
  }

  async function handleNextFromStep2() {
    if (!date || !timeSlot) return;
    setChecking(true);
    setSlotError("");
    const available = await appointmentService.checkSlotAvailability(doctorId, date, timeSlot);
    setChecking(false);
    if (!available) {
      setSlotError("That slot was just booked by another patient. Please choose a different time.");
      return;
    }
    setStep(2);
  }

  async function handleConfirm() {
    setSubmitting(true);
    try {
      const appt = await appointmentService.bookAppointment({
        patientId: "P-1001",
        patientName: user.name,
        doctorId,
        date,
        timeSlot,
      });
      setConfirmed(appt);
      showToast("Appointment request sent.", "success");
    } catch {
      showToast("Unable to book the appointment. Please try again.", "danger");
    } finally {
      setSubmitting(false);
    }
  }

  if (confirmed) {
    return (
      <div className="page">
        <div className="centered-state-page">
          <div className="state-block">
            <CheckCircle2 size={44} color="var(--success)" />
            <h4>Appointment confirmed</h4>
            <p>
              Your appointment with {confirmed.doctorName} on {confirmed.date} at {confirmed.timeSlot} has been requested.
              You'll receive a notification once the doctor confirms.
            </p>
            <div style={{ display: "flex", gap: "var(--sp-3)" }}>
              <Button variant="secondary" onClick={() => navigate("/patient/appointments")}>View my appointments</Button>
              <Button onClick={() => navigate("/patient")}>Back to dashboard</Button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page" style={{ maxWidth: 640 }}>
      <Breadcrumb items={[{ label: "Dashboard", to: "/patient" }, { label: "Book appointment" }]} />
      <div className="page-header"><h1>Book an appointment</h1></div>

      <div className="stepper">
        {STEPS.map((label, i) => (
          <div key={label} style={{ display: "flex", alignItems: "center", gap: "var(--sp-2)" }}>
            <div className={`stepper-step ${i < step ? "done" : i === step ? "current" : ""}`}>
              <span className="stepper-dot">{i < step ? <Check size={13} /> : i + 1}</span>
              {label}
            </div>
            {i < STEPS.length - 1 && <span className="stepper-line" />}
          </div>
        ))}
      </div>

      <Card>
        {step === 0 && (
          <>
            <FormField label="Select a doctor" required htmlFor="doctor">
              <Select id="doctor" value={doctorId} onChange={(e) => setDoctorId(e.target.value)}>
                <option value="">Choose a doctor...</option>
                {doctors.map((d) => (
                  <option key={d.id} value={d.id}>{d.name} - {d.specialization}</option>
                ))}
              </Select>
            </FormField>
            <div className="form-actions">
              <Button onClick={handleNextFromStep1} disabled={!doctorId}>Continue</Button>
            </div>
          </>
        )}

        {step === 1 && (
          <>
            <p style={{ marginTop: 0 }}>Booking with <strong>{selectedDoctor?.name}</strong> ({selectedDoctor?.specialization})</p>
            <div className="form-grid">
              <FormField label="Date" required htmlFor="date">
                <Input id="date" type="date" value={date} onChange={(e) => { setDate(e.target.value); setSlotError(""); }} min={new Date().toISOString().slice(0, 10)} />
              </FormField>
              <FormField label="Time slot" required htmlFor="timeSlot">
                <Select id="timeSlot" value={timeSlot} onChange={(e) => { setTimeSlot(e.target.value); setSlotError(""); }}>
                  <option value="">Choose a time...</option>
                  {TIME_SLOTS.map((t) => <option key={t} value={t}>{t}</option>)}
                </Select>
              </FormField>
            </div>
            {slotError && <Alert variant="danger">{slotError}</Alert>}
            <div className="form-actions">
              <Button variant="secondary" onClick={() => setStep(0)}>Back</Button>
              <Button onClick={handleNextFromStep2} loading={checking} disabled={!date || !timeSlot}>Continue</Button>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <h3 style={{ marginTop: 0 }}>Review your appointment</h3>
            <div className="record-card status-pending" style={{ marginBottom: "var(--sp-4)" }}>
              <p style={{ margin: 0 }}><strong>Doctor:</strong> {selectedDoctor?.name} ({selectedDoctor?.specialization})</p>
              <p style={{ margin: 0 }}><strong>Date:</strong> {date}</p>
              <p style={{ margin: 0 }}><strong>Time:</strong> {timeSlot}</p>
            </div>
            <div className="form-actions">
              <Button variant="secondary" onClick={() => setStep(1)} disabled={submitting}>Back</Button>
              <Button onClick={handleConfirm} loading={submitting}>Confirm appointment</Button>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
