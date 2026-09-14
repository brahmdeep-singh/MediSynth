export default function StatCard({ label, value, meta, icon: Icon }) {
  return (
    <div className="stat-card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <span className="stat-card-label">{label}</span>
        {Icon && <Icon size={18} color="var(--teal-600)" />}
      </div>
      <span className="stat-card-value">{value}</span>
      {meta && <span className="stat-card-meta">{meta}</span>}
    </div>
  );
}
