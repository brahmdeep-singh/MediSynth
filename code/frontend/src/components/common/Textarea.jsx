export default function Textarea({ error, ...props }) {
  return <textarea className={`textarea${error ? " has-error" : ""}`} {...props} />;
}
