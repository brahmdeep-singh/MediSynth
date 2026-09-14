import { useEffect, useState } from "react";
import { FlaskConical, Clock, CheckCircle2 } from "lucide-react";
import StatCard from "../../components/cards/StatCard";
import QuickAction from "../../components/cards/QuickAction";
import { useAuth } from "../../context/AuthContext";
import * as labService from "../../services/labService";

export default function LabDashboard() {
  const { user } = useAuth();
  const [tests, setTests] = useState(null);

  useEffect(() => { labService.getLabTests().then(setTests); }, []);

  const pending = (tests || []).filter((t) => t.status === "pending");
  const completed = (tests || []).filter((t) => t.status === "completed");

  return (
    <div className="page">
      <div className="page-header"><div><h1>Welcome, {user.name}</h1><p>Pending and completed lab test requests.</p></div></div>
      <div className="grid grid-3 section">
        <StatCard label="Pending requests" value={tests ? pending.length : "-"} icon={Clock} />
        <StatCard label="Completed today" value={tests ? completed.length : "-"} icon={CheckCircle2} />
        <StatCard label="Total requests" value={tests ? tests.length : "-"} icon={FlaskConical} />
      </div>
      <div className="grid grid-2">
        <QuickAction icon={FlaskConical} label="Go to lab test queue" description={`${pending.length} awaiting results`} to="/lab/test-queue" />
      </div>
    </div>
  );
}
