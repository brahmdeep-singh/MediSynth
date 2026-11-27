export default function FormField({ label, required, hint, error, htmlFor, children }) {
  return (
    <div className="form-field">
      {label && (
        <label className="form-label" htmlFor={htmlFor}>
          {label}
          {required && <span className="required" aria-hidden="true">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <div className="form-error" role="alert">{error}</div>
      ) : hint ? (
        <div className="form-hint">{hint}</div>
      ) : null}
    </div>
  );
}
