import { Loader2 } from "lucide-react";

export default function Button({
  children, variant = "primary", size = "md", loading = false,
  block = false, icon: Icon, type = "button", ...props
}) {
  const classes = [
    "btn",
    `btn-${variant}`,
    size === "sm" ? "btn-sm" : "",
    block ? "btn-block" : "",
  ].filter(Boolean).join(" ");

  return (
    <button type={type} className={classes} disabled={loading || props.disabled} {...props}>
      {loading ? <Loader2 size={16} className="spin-icon" style={{ animation: "spin 0.7s linear infinite" }} /> : Icon ? <Icon size={16} /> : null}
      {children}
    </button>
  );
}
