import { useEffect, useState } from "react";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Breadcrumb from "../../components/common/Breadcrumb";
import * as pharmacyService from "../../services/pharmacyService";

export default function Prescriptions() {
  const [prescriptions, setPrescriptions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    pharmacyService.getPrescriptions().then((data) => { setPrescriptions(data); setLoading(false); });
  }, []);

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "patientName", label: "Patient", sortable: true },
    { key: "dateIssued", label: "Date issued", sortable: true },
    { key: "medicines", label: "Medicines", render: (row) => row.medicines.map((m) => m.name).join(", ") },
    { key: "status", label: "Status", render: (row) => <StatusBadge status={row.status} /> },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/doctor" }, { label: "Prescriptions" }]} />
      <div className="page-header"><div><h1>Prescriptions</h1><p>Prescriptions you've issued and their pharmacy status.</p></div></div>
      <DataTable
        columns={columns}
        data={prescriptions}
        loading={loading}
        searchPlaceholder="Search by patient..."
        searchKeys={["patientName"]}
        emptyTitle="No prescriptions issued yet"
      />
    </div>
  );
}
