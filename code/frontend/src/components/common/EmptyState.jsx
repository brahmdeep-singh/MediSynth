import { Inbox } from "lucide-react";

export default function EmptyState({ icon: Icon = Inbox, title = "No records found", message, action }) {
  return (
    <div className="state-block">
      <Icon size={36} />
      <h4>{title}</h4>
      {message && <p>{message}</p>}
      {action}
    </div>
  );
}
