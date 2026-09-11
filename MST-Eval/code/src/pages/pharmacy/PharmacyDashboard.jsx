import { useEffect, useState } from "react";
import { Pill, Clock, CheckCircle2 } from "lucide-react";
import StatCard from "../../components/cards/StatCard";
import QuickAction from "../../components/cards/QuickAction";
import { useAuth } from "../../context/AuthContext";
import * as pharmacyService from "../../services/pharmacyService";

export default function PharmacyDashboard() {
  const { user } = useAuth();
  const [prescriptions, setPrescriptions] = useState(null);

  useEffect(() => { pharmacyService.getPrescriptions().then(setPrescriptions); }, []);

  const pending = (prescriptions || []).filter((p) => p.status === "pending");
  const dispensed = (prescriptions || []).filter((p) => p.status === "dispensed");

  return (
    <div className="page">
      <div className="page-header"><div><h1>Welcome, {user.name}</h1><p>Prescriptions awaiting dispensing.</p></div></div>
      <div className="grid grid-3 section">
        <StatCard label="Pending prescriptions" value={prescriptions ? pending.length : "-"} icon={Clock} />
        <StatCard label="Dispensed" value={prescriptions ? dispensed.length : "-"} icon={CheckCircle2} />
        <StatCard label="Total prescriptions" value={prescriptions ? prescriptions.length : "-"} icon={Pill} />
      </div>
      <div className="grid grid-2">
        <QuickAction icon={Pill} label="Go to prescription queue" description={`${pending.length} awaiting dispensing`} to="/pharmacy/queue" />
      </div>
    </div>
  );
}
