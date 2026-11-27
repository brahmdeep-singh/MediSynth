import { useEffect, useState } from "react";
import DataTable from "../../components/tables/DataTable";
import Breadcrumb from "../../components/common/Breadcrumb";
import Modal from "../../components/common/Modal";
import * as recordService from "../../services/recordService";
import Button from "../../components/common/Button";

export default function PatientRecords() {
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewing, setViewing] = useState(null);

  useEffect(() => {
    recordService.getMedicalRecords().then((data) => { setRecords(data); setLoading(false); });
  }, []);

  const columns = [
    { key: "id", label: "Record ID", mono: true },
    { key: "patientName", label: "Patient", sortable: true },
    { key: "diagnosis", label: "Diagnosis", sortable: true },
    { key: "updatedBy", label: "Last updated by" },
    { key: "updatedAt", label: "Date", sortable: true },
    {
      key: "actions", label: "",
      render: (row) => <Button size="sm" variant="secondary" onClick={() => setViewing(row)}>View</Button>,
    },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/doctor" }, { label: "Patient records" }]} />
      <div className="page-header"><div><h1>Patient records</h1><p>Medical history across all patients under your care.</p></div></div>
      <DataTable
        columns={columns}
        data={records}
        loading={loading}
        searchPlaceholder="Search by patient or diagnosis..."
        searchKeys={["patientName", "diagnosis"]}
        emptyTitle="No medical records yet"
      />

      {viewing && (
        <Modal title={viewing.patientName} onClose={() => setViewing(null)}>
          <p><strong>Diagnosis:</strong> {viewing.diagnosis}</p>
          <p><strong>Notes:</strong> {viewing.notes || "-"}</p>
          <p style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)" }}>
            Last updated by {viewing.updatedBy} on {viewing.updatedAt} &middot; <span className="mono">{viewing.id}</span>
          </p>
        </Modal>
      )}
    </div>
  );
}
