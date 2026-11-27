import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";

export default function QuickAction({ icon: Icon, label, description, to }) {
  return (
    <Link to={to} className="quick-action" style={{ textDecoration: "none", color: "inherit" }}>
      <div className="quick-action-icon"><Icon size={18} /></div>
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 600, fontSize: "var(--fs-sm)" }}>{label}</div>
        {description && <div style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)" }}>{description}</div>}
      </div>
      <ChevronRight size={16} color="var(--text-secondary)" />
    </Link>
  );
}
