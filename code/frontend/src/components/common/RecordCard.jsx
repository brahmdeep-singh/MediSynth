// The "chart tab" motif: a status-colored left edge instead of a uniform
// shadow-card, so the accent color is functional (what state is this record
// in?) rather than decorative.
export default function RecordCard({ status, children, className = "" }) {
  return (
    <div className={`record-card status-${status} ${className}`}>
      {children}
    </div>
  );
}
