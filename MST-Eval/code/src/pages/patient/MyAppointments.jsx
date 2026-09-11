import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CalendarPlus } from "lucide-react";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Button from "../../components/common/Button";
import ConfirmDialog from "../../components/common/ConfirmDialog";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useToast } from "../../context/ToastContext";
import * as appointmentService from "../../services/appointmentService";

export default function MyAppointments() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toCancel, setToCancel] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  function load() {
    setLoading(true);
    setError(null);
    appointmentService.getAppointments({ patientId: "P-1001" })
      .then(setAppointments)
      .catch(() => setError("Unable to load your appointments. Please try again."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCancel() {
    setCancelling(true);
    await appointmentService.cancelAppointment(toCancel.id);
    setCancelling(false);
    setToCancel(null);
    showToast("Appointment cancelled.", "success");
    load();
  }

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "doctorName", label: "Doctor", sortable: true },
    { key: "date", label: "Date", sortable: true },
    { key: "timeSlot", label: "Time" },
    { key: "status", label: "Status", sortable: true, render: (row) => <StatusBadge status={row.status} /> },
    {
      key: "actions", label: "",
      render: (row) => (
        row.status === "cancelled" || row.status === "completed" ? null : (
          <div className="table-actions">
            <Button size="sm" variant="danger" onClick={() => setToCancel(row)}>Cancel</Button>
          </div>
        )
      ),
    },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/patient" }, { label: "My appointments" }]} />
      <div className="page-header">
        <div>
          <h1>My appointments</h1>
          <p>View, track, and manage your upcoming and past appointments.</p>
        </div>
        <Button icon={CalendarPlus} onClick={() => navigate("/patient/book-appointment")}>Book appointment</Button>
      </div>

      <DataTable
        columns={columns}
        data={appointments}
        loading={loading}
        error={error}
        onRetry={load}
        searchPlaceholder="Search by doctor or date..."
        searchKeys={["doctorName", "date", "status"]}
        emptyTitle="No appointments yet"
        emptyMessage="Book your first appointment to see it here."
      />

      {toCancel && (
        <ConfirmDialog
          title="Cancel appointment"
          message={`Are you sure you want to cancel your appointment with ${toCancel.doctorName} on ${toCancel.date}?`}
          confirmLabel="Cancel appointment"
          danger
          loading={cancelling}
          onConfirm={handleCancel}
          onCancel={() => setToCancel(null)}
        />
      )}
    </div>
  );
}
