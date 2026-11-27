export default function AuthShell({ title, subtitle, wide = false, children }) {
  return (
    <div className="auth-screen">
      <div className="auth-card" style={wide ? { maxWidth: 640 } : undefined}>
        <div className="auth-brand">
          <div className="sidebar-brand-mark">M</div>
          <div>
            <div style={{ fontWeight: 700, fontSize: "var(--fs-lg)" }}>MediSynth</div>
            <div style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)" }}>Hospital Management System</div>
          </div>
        </div>
        <h2 style={{ fontSize: "var(--fs-lg)" }}>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
        {children}
      </div>
    </div>
  );
}
