import { useNavigate } from "react-router-dom";
import { ShieldAlert } from "lucide-react";
import Button from "../components/common/Button";
import { useAuth } from "../context/AuthContext";
import { HOME_ROUTE_BY_ROLE } from "../routes/navConfig";

export default function UnauthorizedPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="centered-state-page">
      <div className="state-block">
        <ShieldAlert size={40} color="var(--danger)" />
        <h4>You don't have access to this page</h4>
        <p>This area isn't part of your role's workflow in MediSynth.</p>
        <Button onClick={() => navigate(user ? HOME_ROUTE_BY_ROLE[user.role] : "/login")}>
          Go to your dashboard
        </Button>
      </div>
    </div>
  );
}
