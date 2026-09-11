import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// UI-level access control only. Real authorization will be enforced by the
// Spring Boot backend once it exists -- this just keeps the demo's role-based
// navigation honest and prevents one role from browsing to another's pages.
export default function ProtectedRoute({ allowedRoles, children }) {
  const { user } = useAuth();

  if (!user) return <Navigate to="/login" replace />;
  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to="/unauthorized" replace />;
  }
  return children;
}
