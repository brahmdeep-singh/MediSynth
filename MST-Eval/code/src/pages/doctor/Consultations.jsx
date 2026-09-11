import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Button from "../../components/common/Button";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useAuth } from "../../context/AuthContext";
import * as appointmentService from "../../services/appointmentService";

export default function Consultations() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    appointmentService.getAppointments({ doctorId: user.id }).then((data) => {
      setAppointments(data);
      setLoading(false);
    });
  }, [user.id]);

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "patientName", label: "Patient", sortable: true },
    { key: "date", label: "Date", sortable: true },
    { key: "timeSlot", label: "Time" },
    { key: "status", label: "Status", render: (row) => <StatusBadge status={row.status} /> },
    {
      key: "actions", label: "",
      render: (row) => (
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
