import { useEffect, useState } from "react";
import Tabs from "../../components/common/Tabs";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Button from "../../components/common/Button";
import ConfirmDialog from "../../components/common/ConfirmDialog";
import Breadcrumb from "../../components/common/Breadcrumb";
import RecentActivity from "../../components/cards/RecentActivity";
import Card from "../../components/common/Card";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import * as datasetService from "../../services/datasetService";

// Mirrors the Admin swimlane: review access request -> approve/reject -> audit log.
export default function AccessRequests() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [tab, setTab] = useState("requests");
  const [requests, setRequests] = useState([]);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [decisionTarget, setDecisionTarget] = useState(null); // { request, decision }
  const [saving, setSaving] = useState(false);

  function load() {
    setLoading(true);
    Promise.all([datasetService.getDatasetRequests(), datasetService.getAuditLogs()]).then(([r, l]) => {
      setRequests(r); setLogs(l); setLoading(false);
    });
  }
  useEffect(load, []);

  async function handleDecision() {
    setSaving(true);
    await datasetService.decideDatasetRequest(decisionTarget.request.id, decisionTarget.decision, user.name);
    setSaving(false);
    showToast(`Request ${decisionTarget.decision}.`, decisionTarget.decision === "approved" ? "success" : "danger");
    setDecisionTarget(null);
    load();
  }

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "researcherName", label: "Researcher", sortable: true },
    { key: "datasetName", label: "Dataset" },
    { key: "purpose", label: "Purpose" },
    { key: "requestDate", label: "Requested", sortable: true },
    { key: "status", label: "Status", render: (row) => <StatusBadge status={row.status} /> },
    {
      key: "actions", label: "",
      render: (row) => row.status === "pending" && (
        <div className="table-actions">
          <Button size="sm" onClick={() => setDecisionTarget({ request: row, decision: "approved" })}>Approve</Button>
          <Button size="sm" variant="danger" onClick={() => setDecisionTarget({ request: row, decision: "rejected" })}>Reject</Button>
        </div>
      ),
    },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/admin" }, { label: "Access requests" }]} />
      <div className="page-header"><div><h1>Access requests</h1><p>Review researcher requests for synthetic dataset access.</p></div></div>

      <Tabs
        tabs={[{ value: "requests", label: "Requests" }, { value: "audit", label: "Audit log" }]}
        active={tab}
        onChange={setTab}
      />

      {tab === "requests" ? (
        <DataTable
          columns={columns}
          data={requests}
          loading={loading}
          searchPlaceholder="Search by researcher or dataset..."
          searchKeys={["researcherName", "datasetName"]}
          emptyTitle="No access requests"
        />
      ) : (
        <Card>
          <RecentActivity items={logs.map((l) => ({ id: l.id, title: l.action, body: `${l.detail} \u2014 ${l.actor}`, timestamp: l.timestamp }))} />
        </Card>
      )}

      {decisionTarget && (
        <ConfirmDialog
          title={decisionTarget.decision === "approved" ? "Approve access request" : "Reject access request"}
          message={`${decisionTarget.decision === "approved" ? "Grant" : "Deny"} ${decisionTarget.request.researcherName} access to "${decisionTarget.request.datasetName}"? This will be recorded in the audit log.`}
          confirmLabel={decisionTarget.decision === "approved" ? "Approve" : "Reject"}
          danger={decisionTarget.decision === "rejected"}
          loading={saving}
          onConfirm={handleDecision}
          onCancel={() => setDecisionTarget(null)}
        />
      )}
    </div>
  );
}
