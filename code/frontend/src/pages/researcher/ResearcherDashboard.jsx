import { useEffect, useState } from "react";
import { FileSearch, Clock, CheckCircle2 } from "lucide-react";
import StatCard from "../../components/cards/StatCard";
import QuickAction from "../../components/cards/QuickAction";
import Card from "../../components/common/Card";
import EmptyState from "../../components/common/EmptyState";
import { SkeletonTable } from "../../components/common/Skeleton";
import RecordCard from "../../components/common/RecordCard";
import StatusBadge from "../../components/common/Badge";
import { useAuth } from "../../context/AuthContext";
import * as datasetService from "../../services/datasetService";

export default function ResearcherDashboard() {
  const { user } = useAuth();
  const [requests, setRequests] = useState(null);

  useEffect(() => {
    datasetService.getDatasetRequests({ researcherName: user.name }).then(setRequests);
  }, [user.name]);

  const pending = (requests || []).filter((r) => r.status === "pending");
  const approved = (requests || []).filter((r) => r.status === "approved");

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Welcome, {user.name}</h1>
          <p>{user.institution} &middot; request and track access to Synthetix datasets.</p>
        </div>
      </div>

      <div className="grid grid-3 section">
        <StatCard label="Pending requests" value={requests ? pending.length : "-"} icon={Clock} />
        <StatCard label="Approved datasets" value={requests ? approved.length : "-"} icon={CheckCircle2} />
        <StatCard label="Total requests made" value={requests ? requests.length : "-"} icon={FileSearch} />
      </div>

      <div className="grid grid-2 section">
        <Card title="Recent requests">
          {requests === null ? (
            <SkeletonTable rows={2} />
          ) : requests.length === 0 ? (
            <EmptyState title="No requests yet" message="Request access to a synthetic dataset to get started." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-3)" }}>
              {requests.slice(0, 3).map((r) => (
                <RecordCard key={r.id} status={r.status}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <div style={{ fontWeight: 700 }}>{r.datasetName}</div>
                      <div className="mono" style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)" }}>{r.id}</div>
                    </div>
                    <StatusBadge status={r.status} />
                  </div>
                </RecordCard>
              ))}
            </div>
          )}
        </Card>
        <Card title="Quick actions">
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-2)" }}>
            <QuickAction icon={FileSearch} label="Request dataset access" description="Submit a new access request" to="/researcher/request-access" />
            <QuickAction icon={Clock} label="View my requests" description="Track approval status" to="/researcher/my-requests" />
          </div>
        </Card>
      </div>
    </div>
  );
}
