import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import AuthShell from "../components/layout/AuthShell";
import Button from "../components/common/Button";
import Input from "../components/common/Input";
import Select from "../components/common/Select";
import Textarea from "../components/common/Textarea";
import FormField from "../components/common/FormField";
import Alert from "../components/common/Alert";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { HOME_ROUTE_BY_ROLE } from "../routes/navConfig";

const EMAIL_RE = /^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const PHONE_RE = /^\+?[0-9]{7,15}$/;

const EMPTY = {
  name: "", email: "", phone: "", password: "", confirm: "", role: "PATIENT",
  specialization: "", qualifications: "", experienceYears: "", licenseNumber: "", consultationFee: "", availability: "",
  department: "", employeeId: "", institution: "", researchArea: "",
};

// Client-side checks are for convenience only; the server validates everything again.
function validate(f) {
  const e = {};
  if (!f.name.trim()) e.name = "Name is required";
  if (!EMAIL_RE.test(f.email.trim())) e.email = "Enter a valid email address";
  if (!PHONE_RE.test(f.phone.trim())) e.phone = "7-15 digits, optionally starting with +";
  if (f.password.length < 10 || !/[A-Za-z]/.test(f.password) || !/[0-9]/.test(f.password)) {
    e.password = "At least 10 characters, with a letter and a number";
  }
  if (f.confirm !== f.password) e.confirm = "Passwords do not match";
  if (f.role === "DOCTOR") {
    if (!f.specialization.trim()) e.specialization = "Required";
    if (!f.qualifications.trim()) e.qualifications = "Required";
    const y = Number(f.experienceYears);
    if (f.experienceYears === "" || !Number.isInteger(y) || y < 0 || y > 70) e.experienceYears = "Whole number from 0 to 70";
    if (!f.licenseNumber.trim()) e.licenseNumber = "Required";
    if (f.consultationFee !== "" && !(Number(f.consultationFee) >= 0)) e.consultationFee = "Must be 0 or more";
  }
  if (f.role === "LAB_TECHNICIAN") {
    if (!f.department.trim()) e.department = "Required";
    if (!f.employeeId.trim()) e.employeeId = "Required";
  }
  if (f.role === "RESEARCHER") {
    if (!f.institution.trim()) e.institution = "Required";
    if (!f.researchArea.trim()) e.researchArea = "Required";
  }
  return e;
}

export default function RegisterPage() {
  const { register } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  const isDoctor = form.role === "DOCTOR";
  const isLab = form.role === "LAB_TECHNICIAN";
  const isResearcher = form.role === "RESEARCHER";
  const needsApproval = form.role !== "PATIENT";

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError("");
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;

    setSubmitting(true);
    try {
      const result = await register(form);
      if (result.outcome === "ACTIVE") {
        showToast("Account created. Welcome to MediSynth.", "success");
        navigate(HOME_ROUTE_BY_ROLE[result.user.role] || "/", { replace: true });
      } else {
        // Doctor: no token. The one-time reference is passed in router state only (never stored).
        navigate("/registration-pending", { replace: true, state: { reference: result.registrationReference, message: result.message } });
      }
    } catch (err) {
      if (err.fieldErrors) setErrors(err.fieldErrors);
      setFormError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell
      wide
      title="Create your account"
      subtitle="Patients can sign in right away. Doctor, lab technician and researcher accounts are reviewed by an administrator before they can sign in."
    >
      {formError && <Alert variant="danger">{formError}</Alert>}
      <form onSubmit={handleSubmit} noValidate style={{ marginTop: "var(--sp-4)" }}>
        <FormField label="I am registering as" required htmlFor="role">
          <Select id="role" value={form.role} onChange={set("role")}>
            <option value="PATIENT">Patient</option>
            <option value="DOCTOR">Doctor (requires admin approval)</option>
            <option value="LAB_TECHNICIAN">Lab technician (requires admin approval)</option>
            <option value="RESEARCHER">Researcher (requires admin approval)</option>
          </Select>
        </FormField>

        <div className="form-grid">
          <FormField label="Full name" required htmlFor="name" error={errors.name}>
            <Input id="name" value={form.name} onChange={set("name")} autoComplete="name" error={errors.name} />
          </FormField>
          <FormField label="Phone" required htmlFor="phone" error={errors.phone}>
            <Input id="phone" value={form.phone} onChange={set("phone")} autoComplete="tel" placeholder="+919800000000" error={errors.phone} />
          </FormField>
        </div>
        <FormField label="Email" required htmlFor="email" error={errors.email}>
          <Input id="email" type="email" value={form.email} onChange={set("email")} autoComplete="email" error={errors.email} />
        </FormField>
        <div className="form-grid">
          <FormField label="Password" required htmlFor="password" error={errors.password} hint="At least 10 characters, with a letter and a number">
            <Input id="password" type="password" value={form.password} onChange={set("password")} autoComplete="new-password" error={errors.password} />
          </FormField>
          <FormField label="Confirm password" required htmlFor="confirm" error={errors.confirm}>
            <Input id="confirm" type="password" value={form.confirm} onChange={set("confirm")} autoComplete="new-password" error={errors.confirm} />
          </FormField>
        </div>

        {isDoctor && (
          <>
            <h3 style={{ fontSize: "var(--fs-md)", marginTop: "var(--sp-4)" }}>Professional details</h3>
            <div className="form-grid">
              <FormField label="Specialization" required htmlFor="specialization" error={errors.specialization}>
                <Input id="specialization" value={form.specialization} onChange={set("specialization")} error={errors.specialization} />
              </FormField>
              <FormField label="Medical license number" required htmlFor="licenseNumber" error={errors.licenseNumber}>
                <Input id="licenseNumber" value={form.licenseNumber} onChange={set("licenseNumber")} error={errors.licenseNumber} />
              </FormField>
              <FormField label="Years of experience" required htmlFor="experienceYears" error={errors.experienceYears}>
                <Input id="experienceYears" type="number" min="0" max="70" value={form.experienceYears} onChange={set("experienceYears")} error={errors.experienceYears} />
              </FormField>
              <FormField label="Consultation fee" htmlFor="consultationFee" error={errors.consultationFee}>
                <Input id="consultationFee" type="number" min="0" step="0.01" value={form.consultationFee} onChange={set("consultationFee")} error={errors.consultationFee} />
              </FormField>
            </div>
            <FormField label="Qualifications" required htmlFor="qualifications" error={errors.qualifications}>
              <Input id="qualifications" value={form.qualifications} onChange={set("qualifications")} placeholder="e.g. MBBS, MD (Cardiology)" error={errors.qualifications} />
            </FormField>
            <FormField label="Availability" htmlFor="availability" error={errors.availability}>
              <Textarea id="availability" rows={2} value={form.availability} onChange={set("availability")} placeholder="e.g. Mon-Fri 9am-1pm" />
            </FormField>
            <Alert variant="info">Your account stays inactive until an administrator approves your application. You will get a reference to check the status.</Alert>
          </>
        )}

        {isLab && (
          <>
            <h3 style={{ fontSize: "var(--fs-md)", marginTop: "var(--sp-4)" }}>Staff details</h3>
            <div className="form-grid">
              <FormField label="Department" required htmlFor="department" error={errors.department}>
                <Input id="department" value={form.department} onChange={set("department")} placeholder="e.g. Pathology" error={errors.department} />
              </FormField>
              <FormField label="Employee ID" required htmlFor="employeeId" error={errors.employeeId}>
                <Input id="employeeId" value={form.employeeId} onChange={set("employeeId")} error={errors.employeeId} />
              </FormField>
            </div>
            <Alert variant="info">Your account stays inactive until an administrator approves your application.</Alert>
          </>
        )}

        {isResearcher && (
          <>
            <h3 style={{ fontSize: "var(--fs-md)", marginTop: "var(--sp-4)" }}>Research details</h3>
            <div className="form-grid">
              <FormField label="Institution" required htmlFor="institution" error={errors.institution}>
                <Input id="institution" value={form.institution} onChange={set("institution")} error={errors.institution} />
              </FormField>
              <FormField label="Research area" required htmlFor="researchArea" error={errors.researchArea}>
                <Input id="researchArea" value={form.researchArea} onChange={set("researchArea")} error={errors.researchArea} />
              </FormField>
            </div>
            <Alert variant="info">Your account stays inactive until an administrator approves your application.</Alert>
          </>
        )}

        <div style={{ marginTop: "var(--sp-4)" }}>
          <Button type="submit" block loading={submitting}>{needsApproval ? "Submit application" : "Create account"}</Button>
        </div>
      </form>
      <p style={{ marginTop: "var(--sp-4)", fontSize: "var(--fs-sm)" }}>
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthShell>
  );
}
