// Central status -> visual mapping, used by StatusBadge and record-card accents alike.
export const STATUS_VARIANT = {
  confirmed: "success", approved: "success", paid: "success", ready: "success",
  active: "success", dispensed: "success", completed: "success",
  pending: "warning", scheduled: "warning", evaluating: "warning", processing: "warning",
  needs_regeneration: "warning", review: "warning",
  cancelled: "danger", rejected: "danger", failed: "danger", overdue: "danger",
  info: "info",
};

function toLabel(status) {
  return status
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function StatusBadge({ status }) {
  const variant = STATUS_VARIANT[status] || "neutral";
  return <span className={`badge badge-${variant}`}>{toLabel(status)}</span>;
}
