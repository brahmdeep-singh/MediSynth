import { useEffect, useState } from "react";
import { Database, ShieldCheck, Clock, UserCheck } from "lucide-react";
import StatCard from "../../components/cards/StatCard";
import QuickAction from "../../components/cards/QuickAction";
import RecentActivity from "../../components/cards/RecentActivity";
import Card from "../../components/common/Card";
import { useAuth } from "../../context/AuthContext";
import * as datasetService from "../../services/datasetService";
import * as adminService from "../../services/adminService";

export default function AdminDashboard() {
  const { user } = useAuth();
  const [summary, setSummary] = useState(null);
  const [requests, setRequests] = useState(null);
  const [logs, setLogs] = useState(null);
  const [pendingRegistrations, setPendingRegistrations] = useState(null);
  const [regError, setRegError] = useState(false);

  useEffect(() => {
    datasetService.getDatasetSummary().then(setSummary).catch(() => setSummary({ total: "!", ready: 0 }));
    datasetService.getDatasetRequests().then(setRequests);
    datasetService.getAuditLogs().then(setLogs);
    // Pending doctor applications come from the backend, never from a hardcoded list.
    adminService.countPendingRegistrations().then(setPendingRegistrations).catch(() => setRegError(true));
  }, []);

  const pendingRequests = (requests || []).filter((r) => r.status === "pending");

  return (
    <div className="page">
      <div className="page-header"><div><h1>Welcome, {user.name}</h1><p>Synthetix dataset pipeline and access governance.</p></div></div>

      <div className="grid grid-3 section">
        <StatCard label="Pending registrations" value={regError ? "!" : pendingRegistrations ?? "-"} icon={UserCheck} />
        <StatCard label="Synthetic datasets" value={summary ? summary.total : "-"} meta={summary ? `${summary.ready} ready for researchers` : undefined} icon={Database} />
        <StatCard label="Pending access requests" value={requests ? pendingRequests.length : "-"} icon={Clock} />
        <StatCard label="Audit log entries" value={logs ? logs.length : "-"} icon={ShieldCheck} />
      </div>

      <div className="grid grid-2 section">
        <Card title="Quick actions">
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-2)" }}>
            <QuickAction icon={UserCheck} label="Review registrations" description={regError ? "Could not load" : `${pendingRegistrations ?? "..."} pending`} to="/admin/registration-requests" />
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
