import { useState } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import { Copy, Clock } from "lucide-react";
import AuthShell from "../components/layout/AuthShell";
import Button from "../components/common/Button";
import Alert from "../components/common/Alert";

// Shown after a doctor application. The reference is a secret shown ONCE: it is
// kept only in router state (memory), so a refresh intentionally loses it.
export default function PendingApprovalPage() {
  const { state } = useLocation();
  const [copied, setCopied] = useState(false);

  if (!state?.reference) return <Navigate to="/login" replace />;

  async function copy() {
    try {
      await navigator.clipboard.writeText(state.reference);
      setCopied(true);
    } catch { /* clipboard blocked: user can select the text manually */ }
  }

  return (
    <AuthShell title="Application submitted" subtitle="Your account is awaiting administrator approval.">
      <Alert variant="warning">
        <strong>You cannot sign in yet.</strong> An administrator must approve your application first.
      </Alert>
      <div style={{ margin: "var(--sp-4) 0", display: "flex", gap: "var(--sp-2)", alignItems: "center", color: "var(--text-secondary)" }}>
        <Clock size={16} /> Save this reference now. It is shown only once and is the only way to check your status.
      </div>
      <code style={{ display: "block", padding: "var(--sp-3)", background: "var(--surface-alt, #f4f6f8)", borderRadius: 8, wordBreak: "break-all", userSelect: "all" }}>
        {state.reference}
      </code>
      <div style={{ display: "flex", gap: "var(--sp-3)", marginTop: "var(--sp-4)", flexWrap: "wrap" }}>
        <Button variant="secondary" icon={Copy} onClick={copy}>{copied ? "Copied" : "Copy reference"}</Button>
        <Link to="/registration-status"><Button>Check status</Button></Link>
      </div>
      <p style={{ marginTop: "var(--sp-4)", fontSize: "var(--fs-sm)" }}><Link to="/login">Back to sign in</Link></p>
    </AuthShell>
  );
}
