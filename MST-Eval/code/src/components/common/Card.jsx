export default function Card({ title, actions, children, className = "" }) {
  return (
    <div className={`card ${className}`}>
      {title && (
        <div className="card-title-row">
          <h3>{title}</h3>
          {actions}
        </div>
      )}
      {children}
    </div>
  );
}
