import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Plus, Trash2, CheckCircle2 } from "lucide-react";
import Card from "../../components/common/Card";
import Button from "../../components/common/Button";
import FormField from "../../components/common/FormField";
import Textarea from "../../components/common/Textarea";
import Input from "../../components/common/Input";
import Breadcrumb from "../../components/common/Breadcrumb";
import LoadingSpinner from "../../components/common/LoadingSpinner";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import * as appointmentService from "../../services/appointmentService";
import * as recordService from "../../services/recordService";
import * as labService from "../../services/labService";
import * as pharmacyService from "../../services/pharmacyService";

// Implements the Activity Diagram's consultation branch end-to-end:
// Doctor consultation -> Update medical record -> Are lab tests required? ->
// [Yes] Generate lab test request / [No] skip -> merge -> Create prescription ->
// Is medicine required? -> [Yes] Send prescription to pharmacy / [No] skip -> merge.
export default function ConsultationDetail() {
  const { appointmentId } = useParams();
  const { user } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [appointment, setAppointment] = useState(null);
  const [record, setRecord] = useState(null);
  const [loading, setLoading] = useState(true);

  const [diagnosis, setDiagnosis] = useState("");
  const [notes, setNotes] = useState("");
  const [savingRecord, setSavingRecord] = useState(false);

  const [labNeeded, setLabNeeded] = useState(false);
  const [testType, setTestType] = useState("");
  const [orderingTest, setOrderingTest] = useState(false);
  const [orderedTests, setOrderedTests] = useState([]);

  const [medsNeeded, setMedsNeeded] = useState(false);
  const [medicines, setMedicines] = useState([{ name: "", dosage: "" }]);
  const [prescribing, setPrescribing] = useState(false);
  const [prescriptionCreated, setPrescriptionCreated] = useState(false);

  const [completing, setCompleting] = useState(false);

  useEffect(() => {
    async function load() {
      const appt = await appointmentService.getAppointmentById(appointmentId);
      setAppointment(appt);
      if (appt) {
        const rec = await recordService.getOrCreateMedicalRecordForPatient(appt.patientId, appt.patientName);
        setRecord(rec);
        setDiagnosis(rec.diagnosis === "Pending consultation" ? "" : rec.diagnosis);
        setNotes(rec.notes);
      }
      setLoading(false);
    }
    load();
  }, [appointmentId]);

  async function handleSaveRecord() {
    setSavingRecord(true);
    await recordService.updateMedicalRecord(record.id, { diagnosis, notes, updatedBy: user.name });
    setSavingRecord(false);
    showToast("Medical record updated.", "success");
  }

  async function handleOrderTest() {
    if (!testType) return;
    setOrderingTest(true);
    const test = await labService.orderLabTest({
      recordId: record.id, patientName: appointment.patientName, testType, orderedBy: user.name,
    });
    setOrderedTests((prev) => [...prev, test]);
    setTestType("");
    setOrderingTest(false);
    showToast("Lab test request sent.", "success");
  }

  function updateMedicine(i, field, value) {
    setMedicines((prev) => prev.map((m, idx) => (idx === i ? { ...m, [field]: value } : m)));
  }
  function addMedicineRow() {
    setMedicines((prev) => [...prev, { name: "", dosage: "" }]);
  }
  function removeMedicineRow(i) {
    setMedicines((prev) => prev.filter((_, idx) => idx !== i));
  }

  async function handleCreatePrescription() {
    setPrescribing(true);
    await pharmacyService.createPrescription({
      recordId: record.id,
      patientName: appointment.patientName,
      prescribedBy: user.name,
      medicines: medicines.filter((m) => m.name.trim()),
    });
    setPrescribing(false);
    setPrescriptionCreated(true);
    showToast("Prescription sent to pharmacy.", "success");
  }

  async function handleCompleteConsultation() {
    setCompleting(true);
    await appointmentService.completeAppointment(appointmentId);
    setCompleting(false);
    showToast("Consultation marked as complete.", "success");
    navigate("/doctor/consultations");
  }

  if (loading) return <div className="page"><LoadingSpinner label="Loading consultation..." /></div>;
  if (!appointment) return <div className="page"><p>Appointment not found.</p></div>;

  return (
    <div className="page" style={{ maxWidth: 720 }}>
      <Breadcrumb items={[{ label: "Dashboard", to: "/doctor" }, { label: "Consultations", to: "/doctor/consultations" }, { label: appointment.patientName }]} />
      <div className="page-header">
        <div>
          <h1>Consultation - {appointment.patientName}</h1>
          <p>{appointment.date} at {appointment.timeSlot} &middot; <span className="mono">{appointment.id}</span></p>
        </div>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-5)" }}>
        <Card title="1. Update medical record">
          <FormField label="Diagnosis" required htmlFor="diagnosis">
            <Input id="diagnosis" value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)} placeholder="e.g. Seasonal allergic rhinitis" />
          </FormField>
          <FormField label="Consultation notes" htmlFor="notes">
            <Textarea id="notes" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Observations, patient history, follow-up plan..." />
          </FormField>
          <Button onClick={handleSaveRecord} loading={savingRecord} disabled={!diagnosis}>Save record</Button>
        </Card>

        <Card title="2. Are lab tests required?">
          <div className="radio-row">
            <input type="radio" id="lab-yes" name="lab" checked={labNeeded} onChange={() => setLabNeeded(true)} />
            <label htmlFor="lab-yes">Yes, order lab tests</label>
          </div>
          <div className="radio-row">
            <input type="radio" id="lab-no" name="lab" checked={!labNeeded} onChange={() => setLabNeeded(false)} />
            <label htmlFor="lab-no">No, continue treatment</label>
          </div>

          {labNeeded && (
            <div style={{ marginTop: "var(--sp-4)", paddingTop: "var(--sp-4)", borderTop: "1px solid var(--border)" }}>
              <FormField label="Test type" htmlFor="testType">
                <Input id="testType" value={testType} onChange={(e) => setTestType(e.target.value)} placeholder="e.g. Complete Blood Count" />
              </FormField>
              <Button size="sm" icon={Plus} onClick={handleOrderTest} loading={orderingTest} disabled={!testType}>Generate lab test request</Button>

              {orderedTests.length > 0 && (
                <ul style={{ marginTop: "var(--sp-4)", paddingLeft: "var(--sp-5)" }}>
                  {orderedTests.map((t) => <li key={t.id} style={{ fontSize: "var(--fs-sm)" }}>{t.testType} <span className="mono" style={{ color: "var(--text-secondary)" }}>({t.id})</span> - sent to lab</li>)}
                </ul>
              )}
            </div>
          )}
        </Card>

        <Card title="3. Create prescription">
          <div className="radio-row">
            <input type="radio" id="meds-yes" name="meds" checked={medsNeeded} onChange={() => setMedsNeeded(true)} />
            <label htmlFor="meds-yes">Yes, medicine is required</label>
          </div>
          <div className="radio-row">
            <input type="radio" id="meds-no" name="meds" checked={!medsNeeded} onChange={() => setMedsNeeded(false)} />
            <label htmlFor="meds-no">No medicine required</label>
          </div>

          {medsNeeded && !prescriptionCreated && (
            <div style={{ marginTop: "var(--sp-4)", paddingTop: "var(--sp-4)", borderTop: "1px solid var(--border)" }}>
              {medicines.map((m, i) => (
                <div key={i} className="form-grid" style={{ alignItems: "end", marginBottom: "var(--sp-2)" }}>
                  <FormField label={i === 0 ? "Medicine" : undefined}>
                    <Input value={m.name} onChange={(e) => updateMedicine(i, "name", e.target.value)} placeholder="e.g. Cetirizine 10mg" />
                  </FormField>
                  <div style={{ display: "flex", gap: "var(--sp-2)" }}>
                    <FormField label={i === 0 ? "Dosage" : undefined} htmlFor={`dose-${i}`}>
                      <Input id={`dose-${i}`} value={m.dosage} onChange={(e) => updateMedicine(i, "dosage", e.target.value)} placeholder="e.g. Once daily" />
                    </FormField>
                    {medicines.length > 1 && (
                      <Button variant="ghost" size="sm" onClick={() => removeMedicineRow(i)} aria-label="Remove medicine"><Trash2 size={14} /></Button>
                    )}
                  </div>
                </div>
              ))}
              <Button variant="secondary" size="sm" icon={Plus} onClick={addMedicineRow}>Add another medicine</Button>
              <div style={{ marginTop: "var(--sp-4)" }}>
                <Button onClick={handleCreatePrescription} loading={prescribing} disabled={!medicines.some((m) => m.name.trim())}>
                  Send prescription to pharmacy
                </Button>
              </div>
            </div>
          )}
          {prescriptionCreated && (
            <div style={{ marginTop: "var(--sp-4)", display: "flex", alignItems: "center", gap: "var(--sp-2)", color: "var(--success)" }}>
              <CheckCircle2 size={18} /> Prescription sent to pharmacy.
            </div>
          )}
        </Card>

        <div className="form-actions" style={{ justifyContent: "flex-end" }}>
          <Button onClick={handleCompleteConsultation} loading={completing}>Complete consultation</Button>
        </div>
      </div>
    </div>
  );
}
