import { useEffect, useState } from "react";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Button from "../../components/common/Button";
import Modal from "../../components/common/Modal";
import Textarea from "../../components/common/Textarea";
import FormField from "../../components/common/FormField";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useToast } from "../../context/ToastContext";
import * as labService from "../../services/labService";

// Mirrors the Lab Technician swimlane: receive lab test request -> perform test -> update lab report.
export default function LabTestQueue() {
  const { showToast } = useToast();
  const [tests, setTests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submittingFor, setSubmittingFor] = useState(null);
  const [result, setResult] = useState("");
  const [saving, setSaving] = useState(false);

  function load() {
    setLoading(true);
    labService.getLabTests().then((data) => { setTests(data); setLoading(false); });
  }
  useEffect(load, []);

  async function handleSubmitResult() {
    setSaving(true);
    await labService.submitLabResult(submittingFor.id, result);
    setSaving(false);
    setSubmittingFor(null);
    setResult("");
    showToast("Lab report updated.", "success");
    load();
  }

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "patientName", label: "Patient", sortable: true },
    { key: "testType", label: "Test type", sortable: true },
    { key: "orderedBy", label: "Ordered by" },
    { key: "status", label: "Status", render: (row) => <StatusBadge status={row.status} /> },
    {
      key: "actions", label: "",
      render: (row) => row.status === "pending" && (
        <Button size="sm" onClick={() => { setSubmittingFor(row); setResult(""); }}>Submit result</Button>
      ),
    },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/lab" }, { label: "Lab test queue" }]} />
      <div className="page-header"><div><h1>Lab test queue</h1><p>Pending lab test requests from doctors.</p></div></div>
      <DataTable
        columns={columns}
        data={tests}
        loading={loading}
        searchPlaceholder="Search by patient or test type..."
        searchKeys={["patientName", "testType"]}
        emptyTitle="No lab test requests"
      />

      {submittingFor && (
        <Modal title={`Submit result - ${submittingFor.testType}`} onClose={() => setSubmittingFor(null)}>
          <p>Patient: <strong>{submittingFor.patientName}</strong></p>
          <FormField label="Result" required htmlFor="result">
            <Textarea id="result" value={result} onChange={(e) => setResult(e.target.value)} placeholder="Enter the test result and any relevant observations..." />
          </FormField>
          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setSubmittingFor(null)} disabled={saving}>Cancel</Button>
            <Button onClick={handleSubmitResult} loading={saving} disabled={!result.trim()}>Update lab report</Button>
          </div>
        </Modal>
      )}
    </div>
  );
}
