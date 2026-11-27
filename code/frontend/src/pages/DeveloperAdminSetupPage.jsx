import { useState } from "react";
import { Link } from "react-router-dom";
import AuthShell from "../components/layout/AuthShell";
import Button from "../components/common/Button";
import Input from "../components/common/Input";
import FormField from "../components/common/FormField";
import Alert from "../components/common/Alert";
import { bootstrapAdmin } from "../services/authService";

// "I am a developer and want to create an admin account."
// This is NOT a role choice on the public registration form. It calls the secure
// one-time bootstrap endpoint. The secret is typed here at runtime, sent only in
// the X-Bootstrap-Secret header, and cleared right after submit. It is never
// stored and never read from env/config. Success is shown only after the server confirms.
export default function DeveloperAdminSetupPage() {
  const [form, setForm] = useState({ name: "", email: "", phone: "", password: "", secret: "" });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [created, setCreated] = useState(null);
  const [loading, setLoading] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError(""); setErrors({});
    if (!form.secret) { setErrors({ secret: "Enter the bootstrap secret" }); return; }
    setLoading(true);
    try {
      const { secret, ...details } = form;
      const user = await bootstrapAdmin(secret, details);
      setCreated(user); // server confirmed (201)
    } catch (err) {
      if (err.fieldErrors) setErrors(err.fieldErrors);
      setFormError(
        err.status === 403 ? "Bootstrap was refused. Check the secret, or an admin may already exist."
        : err.status === 404 ? "Admin bootstrap is not available on this server."
        : err.message
      );
    } finally {
      setForm((f) => ({ ...f, secret: "", password: "" })); // never keep secrets around
      setLoading(false);
    }
  }

  if (created) {
    return (
      <AuthShell title="Admin account created">
        <Alert variant="success">The server created the administrator account for <strong>{created.email}</strong>. The bootstrap endpoint is now closed.</Alert>
        <p style={{ marginTop: "var(--sp-4)" }}><Link to="/login">Sign in as admin</Link></p>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Developer: create the first admin" subtitle="One-time setup. Requires the bootstrap secret configured on the server (ADMIN_BOOTSTRAP_SECRET).">
      {formError && <Alert variant="danger">{formError}</Alert>}
      <form onSubmit={handleSubmit} noValidate autoComplete="off" style={{ marginTop: "var(--sp-4)" }}>
        <FormField label="Bootstrap secret" required htmlFor="secret" error={errors.secret} hint="Typed at runtime; sent only in a request header and never stored.">
          <Input id="secret" type="password" value={form.secret} onChange={set("secret")} autoComplete="off" error={errors.secret} />
        </FormField>
        <FormField label="Admin name" required htmlFor="name" error={errors.name}>
          <Input id="name" value={form.name} onChange={set("name")} error={errors.name} />
        </FormField>
        <div className="form-grid">
          <FormField label="Email" required htmlFor="email" error={errors.email}>
            <Input id="email" type="email" value={form.email} onChange={set("email")} error={errors.email} />
          </FormField>
          <FormField label="Phone" required htmlFor="phone" error={errors.phone}>
            <Input id="phone" value={form.phone} onChange={set("phone")} error={errors.phone} />
          </FormField>
        </div>
        <FormField label="Password" required htmlFor="password" error={errors.password} hint="At least 10 characters, with a letter and a number">
          <Input id="password" type="password" value={form.password} onChange={set("password")} autoComplete="new-password" error={errors.password} />
        </FormField>
        <Button type="submit" block loading={loading}>Create admin account</Button>
      </form>
      <p style={{ marginTop: "var(--sp-4)", fontSize: "var(--fs-sm)" }}><Link to="/login">Back to sign in</Link></p>
    </AuthShell>
  );
}
