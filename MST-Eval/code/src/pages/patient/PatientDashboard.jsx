import { useEffect, useState } from "react";
import { CalendarPlus, FileText, Receipt, CalendarCheck } from "lucide-react";
import StatCard from "../../components/cards/StatCard";
import QuickAction from "../../components/cards/QuickAction";
import AppointmentCard from "../../components/cards/AppointmentCard";
import Card from "../../components/common/Card";
import EmptyState from "../../components/common/EmptyState";
import { SkeletonTable } from "../../components/common/Skeleton";
import { useAuth } from "../../context/AuthContext";
import * as appointmentService from "../../services/appointmentService";
import * as billingService from "../../services/billingService";

export default function PatientDashboard() {
  const { user } = useAuth();
  const patientId = "P-1001"; // demo patient linked to the logged-in user
  const [appointments, setAppointments] = useState(null);
  const [bills, setBills] = useState(null);

  useEffect(() => {
    appointmentService.getAppointments({ patientId }).then(setAppointments);
    billingService.getBills({ patientName: user.name }).then(setBills);
  }, [user.name]);

  const upcoming = (appointments || []).filter((a) => a.status !== "cancelled" && a.status !== "completed");
  const pendingBills = (bills || []).filter((b) => b.status !== "paid");

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Welcome back, {user.name.split(" ")[0]}</h1>
          <p>Here's what's happening with your care at MediSynth.</p>
        </div>
      </div>

      <div className="grid grid-3 section">
        <StatCard label="Upcoming appointments" value={appointments ? upcoming.length : "-"} icon={CalendarCheck} />
        <StatCard label="Pending bills" value={bills ? pendingBills.length : "-"} icon={Receipt} />
        <StatCard label="Medical reports" value={appointments ? appointments.filter(a => a.status === "completed").length : "-"} meta="From completed visits" icon={FileText} />
      </div>

      <div className="grid grid-2 section">
        <Card title="Upcoming appointments">
          {appointments === null ? (
            <SkeletonTable rows={2} />
          ) : upcoming.length === 0 ? (
            <EmptyState title="No upcoming appointments" message="Book an appointment with a doctor to get started." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-3)" }}>
              {upcoming.slice(0, 3).map((a) => (
                <AppointmentCard key={a.id} appointment={a} personLabel={a.doctorName} />
              ))}
            </div>
          )}
        </Card>

        <Card title="Quick actions">
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-2)" }}>
            <QuickAction icon={CalendarPlus} label="Book an appointment" description="Find a doctor and schedule a visit" to="/patient/book-appointment" />
            <QuickAction icon={FileText} label="View medical reports" description="See notes from your past visits" to="/patient/medical-reports" />
            <QuickAction icon={Receipt} label="Pay a bill" description={`${pendingBills.length} pending`} to="/patient/billing" />
          </div>
        </Card>
      </div>
    </div>
  );
}
