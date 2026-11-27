import { useState } from "react";
import { useNavigate, useLocation, Link } from "react-router-dom";
import { Mail } from "lucide-react";
import Button from "../components/common/Button";
import Input from "../components/common/Input";
import FormField from "../components/common/FormField";
import Alert from "../components/common/Alert";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { users, ROLE_LABELS } from "../data/users";
import { HOME_ROUTE_BY_ROLE } from "../routes/navConfig";
import { USE_MOCKS } from "../services/api";

export default function LoginPage() {
  const { login, loginAsDemoRole, loading, error } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [formError, setFormError] = useState("");

  function handleLoggedIn(user) {
    showToast(`Welcome back, ${user.name.split(" ")[0]}.`, "success");
    // Land on the dashboard for the role the SERVER returned; only honour a
    // saved destination if it belongs to that role's area.
    const home = HOME_ROUTE_BY_ROLE[user.role] || "/";
    const from = location.state?.from;
    const dest = from && from.startsWith(home) ? from : home;
    navigate(dest, { replace: true });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError("");
    if (!email || !password) {
      setFormError("Please enter both your email and password.");
      return;
    }
    try {
      const user = await login(email, password);
      handleLoggedIn(user);
    } catch {
      // error already surfaced via AuthContext's `error`
    }
  }

  async function handleDemoRole(userId) {
    const user = await loginAsDemoRole(userId);
    handleLoggedIn(user);
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="sidebar-brand-mark">M</div>
          <div>
            <div style={{ fontWeight: 700, fontSize: "var(--fs-lg)" }}>MediSynth</div>
            <div style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)" }}>Hospital Management System</div>
          </div>
        </div>

        <h2 style={{ fontSize: "var(--fs-lg)" }}>Sign in</h2>
        <p>Enter your credentials to access your MediSynth dashboard.</p>

        {(formError || error) && <Alert variant="danger">{formError || error}</Alert>}

        <form onSubmit={handleSubmit} noValidate style={{ marginTop: "var(--sp-4)" }}>
          <FormField label="Email" required htmlFor="email">
            <Input
              id="email" type="email" icon={Mail} autoComplete="username"
              placeholder="you@medisynth.org" value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </FormField>
          <FormField label="Password" required htmlFor="password">
            <Input
              id="password" type="password" autoComplete="current-password"
              placeholder="********" value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </FormField>
          <Button type="submit" block loading={loading}>Sign in</Button>
        </form>

        <div style={{ marginTop: "var(--sp-5)", display: "flex", flexDirection: "column", gap: "var(--sp-2)", fontSize: "var(--fs-sm)" }}>
          <span>New here? <Link to="/register">Create an account</Link></span>
          <Link to="/registration-status">Check an application status</Link>
          <Link to="/developer/admin-setup">I am a developer and want to create an admin account</Link>
        </div>

        {USE_MOCKS && (
          <>
            <div className="auth-divider">demo mode: continue with a demo role</div>
            <div className="auth-demo-roles">
              {users.map((u) => (
                <button key={u.id} className="auth-demo-role-btn" onClick={() => handleDemoRole(u.id)}>
                  <span>{u.name}</span>
                  <span style={{ color: "var(--text-secondary)", fontSize: "var(--fs-xs)" }}>{ROLE_LABELS[u.role]}</span>
                </button>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
