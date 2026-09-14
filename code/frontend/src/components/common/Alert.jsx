import { Info, CheckCircle2, AlertTriangle, XCircle } from "lucide-react";

const ICONS = { info: Info, success: CheckCircle2, warning: AlertTriangle, danger: XCircle };

export default function Alert({ variant = "info", children }) {
  const Icon = ICONS[variant];
  return (
    <div className={`alert alert-${variant}`} role={variant === "danger" ? "alert" : "status"}>
      <Icon size={18} />
      <div>{children}</div>
    </div>
  );
}
