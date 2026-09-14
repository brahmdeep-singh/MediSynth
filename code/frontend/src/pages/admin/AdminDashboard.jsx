import { useEffect, useState } from "react";
import { Database, ShieldCheck, Clock } from "lucide-react";
import StatCard from "../../components/cards/StatCard";
import QuickAction from "../../components/cards/QuickAction";
import RecentActivity from "../../components/cards/RecentActivity";
import Card from "../../components/common/Card";
import { useAuth } from "../../context/AuthContext";
import * as datasetService from "../../services/datasetService";

export default function AdminDashboard() {
  const { user } = useAuth();
  const [datasets, setDatasets] = useState(null);
  const [requests, setRequests] = useState(null);
  const [logs, setLogs] = useState(null);

  useEffect(() => {
    datasetService.getDatasets().then(setDatasets);
    datasetService.getDatasetRequests().then(setRequests);
    datasetService.getAuditLogs().then(setLogs);
  }, []);

  const pendingRequests = (requests || []).filter((r) => r.status === "pending");

  return (
    <div className="page">
      <div className="page-header"><div><h1>Welcome, {user.name}</h1><p>Synthetix dataset pipeline and access governance.</p></div></div>

      <div className="grid grid-3 section">
        <StatCard label="Synthetic datasets" value={datasets ? datasets.length : "-"} icon={Database} />
        <StatCard label="Pending access requests" value={requests ? pendingRequests.length : "-"} icon={Clock} />
        <StatCard label="Audit log entries" value={logs ? logs.length : "-"} icon={ShieldCheck} />
      </div>

      <div className="grid grid-2 section">
        <Card title="Quick actions">
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-2)" }}>
            <QuickAction icon={Database} label="Prepare a new dataset" description="Generate a Synthetix batch" to="/admin/dataset-preparation" />
            <QuickAction icon={ShieldCheck} label="Review access requests" description={`${pendingRequests.length} pending`} to="/admin/access-requests" />
          </div>
        </Card>
        <Card title="Recent activity">
          <RecentActivity items={(logs || []).slice(0, 4).map((l) => ({ id: l.id, title: l.action, body: l.detail, timestamp: l.timestamp }))} />
        </Card>
      </div>
    </div>
  );
}
