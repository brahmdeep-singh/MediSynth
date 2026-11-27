export default function Select({ error, children, ...props }) {
  return (
    <select className={`select${error ? " has-error" : ""}`} {...props}>
      {children}
    </select>
  );
}
