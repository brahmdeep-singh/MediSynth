import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";

export default function Input({ icon: Icon, type = "text", error, ...props }) {
  const [show, setShow] = useState(false);
  const isPassword = type === "password";
  const resolvedType = isPassword ? (show ? "text" : "password") : type;

  if (Icon || isPassword) {
    return (
      <div className="input-with-icon">
        {Icon && <Icon size={16} />}
        <input type={resolvedType} className={`input${error ? " has-error" : ""}`} {...props} />
        {isPassword && (
          <button
            type="button"
            className="input-toggle-btn"
            onClick={() => setShow((s) => !s)}
            aria-label={show ? "Hide password" : "Show password"}
          >
            {show ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        )}
      </div>
    );
  }

  return <input type={type} className={`input${error ? " has-error" : ""}`} {...props} />;
}
