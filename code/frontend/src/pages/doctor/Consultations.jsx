import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Button from "../../components/common/Button";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import * as appointmentService from "../../services/appointmentService";

export default function Consultations() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);

  function load() {
    appointmentService.getAppointments({ doctorId: user.id })
      .then(setAppointments)
      .catch((e) => showToast(e.message, "danger"))
      .finally(() => setLoading(false));
  }
  useEffect(load, [user.id]); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleConfirm(id) {
    try {
      await appointmentService.confirmAppointment(id);
      showToast("Appointment confirmed.", "success");
      load();
    } catch (e) {
      showToast(e.message, "danger");
    }
  }

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "patientName", label: "Patient", sortable: true },
    { key: "date", label: "Date", sortable: true },
    { key: "timeSlot", label: "Time" },
    { key: "status", label: "Status", render: (row) => <StatusBadge status={row.status} /> },
    {
      key: "actions", label: "",
      render: (row) => row.status === "pending" ? (
        <div className="table-actions">
          <Button size="sm" variant="secondary" onClick={() => handleConfirm(row.id)}>Confirm</Button>
        </div>
      ) : (
        <Button size="sm" onClick={() => navigate(`/doctor/consultations/${row.id}`)} disabled={row.status === "cancelled"}>
          {row.status === "completed" ? "View" : "Start consultation"}
        </Button>
      ),
    },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/doctor" }, { label: "Consultations" }]} />
      <div className="page-header"><div><h1>Consultations</h1><p>Your scheduled appointments and their consultation status.</p></div></div>
      <DataTable
        columns={columns}
        data={appointments}
        loading={loading}
        searchPlaceholder="Search by patient..."
        searchKeys={["patientName", "date", "status"]}
        emptyTitle="No appointments assigned"
      />
    </div>
  );
}
