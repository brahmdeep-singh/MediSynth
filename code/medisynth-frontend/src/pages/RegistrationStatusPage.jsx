import { useState } from "react";
import { Link } from "react-router-dom";
import AuthShell from "../components/layout/AuthShell";
import Button from "../components/common/Button";
import Input from "../components/common/Input";
import FormField from "../components/common/FormField";
import Alert from "../components/common/Alert";
import { getRegistrationStatus } from "../services/authService";

const VARIANT = { PENDING_APPROVAL: "warning", APPROVED: "success", REJECTED: "danger" };
const TITLE = { PENDING_APPROVAL: "Awaiting approval", APPROVED: "Approved", REJECTED: "Not approved" };

export default function RegistrationStatusPage() {
  const [reference, setReference] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(""); setResult(null);
    if (!reference.trim()) { setError("Enter your registration reference."); return; }
    setLoading(true);
    try {
      setResult(await getRegistrationStatus(reference));
    } catch (err) {
      setError(err.status === 404 ? "No application found for that reference." : err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell title="Check application status" subtitle="Enter the registration reference you received when you applied.">
      <form onSubmit={handleSubmit} noValidate style={{ marginTop: "var(--sp-4)" }}>
        <FormField label="Registration reference" required htmlFor="reference">
          <Input id="reference" value={reference} onChange={(e) => setReference(e.target.value)} autoComplete="off" spellCheck={false} />
        </FormField>
        <Button type="submit" block loading={loading}>Check status</Button>
      </form>
      {error && <div style={{ marginTop: "var(--sp-4)" }}><Alert variant="danger">{error}</Alert></div>}
      {result && (
        <div style={{ marginTop: "var(--sp-4)" }}>
          <Alert variant={VARIANT[result.status] || "info"}>
            <strong>{TITLE[result.status] || result.status}.</strong> {result.message}
            {result.status === "REJECTED" && result.rejectionReason && <div>Reason: {result.rejectionReason}</div>}
          </Alert>
          {result.status === "APPROVED" && <p><Link to="/login">Go to sign in</Link></p>}
        </div>
      )}
      <p style={{ marginTop: "var(--sp-4)", fontSize: "var(--fs-sm)" }}><Link to="/login">Back to sign in</Link></p>
    </AuthShell>
  );
}
