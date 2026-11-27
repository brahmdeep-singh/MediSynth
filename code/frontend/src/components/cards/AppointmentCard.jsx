import { CalendarDays, Clock, User } from "lucide-react";
import RecordCard from "../common/RecordCard";
import StatusBadge from "../common/Badge";

export default function AppointmentCard({ appointment, personLabel }) {
  return (
    <RecordCard status={appointment.status}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "var(--sp-3)" }}>
        <div>
          <div style={{ fontWeight: 700, marginBottom: 4 }}>{personLabel}</div>
          <div style={{ display: "flex", gap: "var(--sp-4)", color: "var(--text-secondary)", fontSize: "var(--fs-sm)", flexWrap: "wrap" }}>
            <span style={{ display: "flex", alignItems: "center", gap: 5 }}><CalendarDays size={14} /> {appointment.date}</span>
            <span style={{ display: "flex", alignItems: "center", gap: 5 }}><Clock size={14} /> {appointment.timeSlot}</span>
          </div>
        </div>
        <StatusBadge status={appointment.status} />
      </div>
      <div className="mono" style={{ color: "var(--text-secondary)", marginTop: "var(--sp-2)" }}>{appointment.id}</div>
    </RecordCard>
  );
}
