import { useEffect, useState } from "react";
import { Stethoscope, ClipboardList, Pill } from "lucide-react";
import StatCard from "../../components/cards/StatCard";
import QuickAction from "../../components/cards/QuickAction";
import AppointmentCard from "../../components/cards/AppointmentCard";
import Card from "../../components/common/Card";
import EmptyState from "../../components/common/EmptyState";
import { SkeletonTable } from "../../components/common/Skeleton";
import { useAuth } from "../../context/AuthContext";
import * as appointmentService from "../../services/appointmentService";
import * as recordService from "../../services/recordService";

export default function DoctorDashboard() {
  const { user } = useAuth();
  const [appointments, setAppointments] = useState(null);
  const [records, setRecords] = useState(null);

  useEffect(() => {
    appointmentService.getAppointments({ doctorId: user.id }).then(setAppointments);
    recordService.getMedicalRecords().then(setRecords);
  }, [user.id]);

  const today = (appointments || []).filter((a) => a.status !== "cancelled" && a.status !== "completed");

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Welcome, {user.name}</h1>
          <p>{user.specialization} &middot; here's your consultation queue.</p>
        </div>
      </div>

      <div className="grid grid-3 section">
        <StatCard label="Appointments today" value={appointments ? today.length : "-"} icon={Stethoscope} />
        <StatCard label="Patient records" value={records ? records.length : "-"} icon={ClipboardList} />
        <StatCard label="Awaiting prescriptions" value={records ? records.filter(r => r.diagnosis && r.diagnosis !== "Pending consultation").length : "-"} icon={Pill} />
      </div>

      <div className="grid grid-2 section">
        <Card title="Today's appointments">
          {appointments === null ? (
            <SkeletonTable rows={2} />
          ) : today.length === 0 ? (
            <EmptyState title="No appointments scheduled" />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-3)" }}>
              {today.slice(0, 3).map((a) => <AppointmentCard key={a.id} appointment={a} personLabel={a.patientName} />)}
            </div>
          )}
        </Card>
        <Card title="Quick actions">
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-2)" }}>
            <QuickAction icon={Stethoscope} label="Start a consultation" description="Review a scheduled appointment" to="/doctor/consultations" />
            <QuickAction icon={ClipboardList} label="View patient records" description="Search medical history" to="/doctor/patient-records" />
            <QuickAction icon={Pill} label="Manage prescriptions" description="View prescriptions you've issued" to="/doctor/prescriptions" />
          </div>
        </Card>
      </div>
    </div>
  );
}
