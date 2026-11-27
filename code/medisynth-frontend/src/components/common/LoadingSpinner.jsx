export default function LoadingSpinner({ label = "Loading..." }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "var(--sp-5) 0", color: "var(--text-secondary)" }}>
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
