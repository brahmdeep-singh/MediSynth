import { useEffect, useState } from "react";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Button from "../../components/common/Button";
import ConfirmDialog from "../../components/common/ConfirmDialog";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useToast } from "../../context/ToastContext";
import * as pharmacyService from "../../services/pharmacyService";

// Mirrors the Pharmacist swimlane: receive prescription -> process prescription -> medicine dispensed.
export default function PrescriptionQueue() {
  const { showToast } = useToast();
  const [prescriptions, setPrescriptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dispensing, setDispensing] = useState(null);
  const [saving, setSaving] = useState(false);

  function load() {
    setLoading(true);
    pharmacyService.getPrescriptions().then((data) => { setPrescriptions(data); setLoading(false); });
  }
  useEffect(load, []);

  async function handleDispense() {
    setSaving(true);
    await pharmacyService.dispenseMedicine(dispensing.id);
    setSaving(false);
    setDispensing(null);
    showToast("Medicine dispensed.", "success");
    load();
  }

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "patientName", label: "Patient", sortable: true },
    { key: "medicines", label: "Medicines", render: (row) => row.medicines.map((m) => m.name).join(", ") },
    { key: "prescribedBy", label: "Prescribed by" },
    { key: "status", label: "Status", render: (row) => <StatusBadge status={row.status} /> },
    {
      key: "actions", label: "",
      render: (row) => row.status === "pending" && (
        <Button size="sm" onClick={() => setDispensing(row)}>Dispense</Button>
      ),
    },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/pharmacy" }, { label: "Prescription queue" }]} />
      <div className="page-header"><div><h1>Prescription queue</h1><p>Prescriptions sent by doctors, awaiting dispensing.</p></div></div>
      <DataTable
        columns={columns}
        data={prescriptions}
        loading={loading}
        searchPlaceholder="Search by patient..."
        searchKeys={["patientName"]}
        emptyTitle="No prescriptions in queue"
      />

      {dispensing && (
        <ConfirmDialog
          title="Dispense medicine"
          message={`Confirm that all medicines for ${dispensing.patientName}'s prescription (${dispensing.id}) have been dispensed.`}
          confirmLabel="Confirm dispensed"
          loading={saving}
          onConfirm={handleDispense}
          onCancel={() => setDispensing(null)}
        />
      )}
    </div>
  );
}
