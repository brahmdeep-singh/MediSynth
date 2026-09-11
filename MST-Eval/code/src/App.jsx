import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { ToastProvider } from "./context/ToastContext";
import ProtectedRoute from "./routes/ProtectedRoute";
import { HOME_ROUTE_BY_ROLE } from "./routes/navConfig";
import AppLayout from "./layouts/AppLayout";

import LoginPage from "./pages/LoginPage";
import NotFoundPage from "./pages/NotFoundPage";
import UnauthorizedPage from "./pages/UnauthorizedPage";

import PatientDashboard from "./pages/patient/PatientDashboard";
import BookAppointment from "./pages/patient/BookAppointment";
import MyAppointments from "./pages/patient/MyAppointments";
import MedicalReports from "./pages/patient/MedicalReports";
import Billing from "./pages/patient/Billing";

import DoctorDashboard from "./pages/doctor/DoctorDashboard";
import Consultations from "./pages/doctor/Consultations";
import ConsultationDetail from "./pages/doctor/ConsultationDetail";
import PatientRecords from "./pages/doctor/PatientRecords";
import Prescriptions from "./pages/doctor/Prescriptions";

import LabDashboard from "./pages/lab/LabDashboard";
import LabTestQueue from "./pages/lab/LabTestQueue";

import PharmacyDashboard from "./pages/pharmacy/PharmacyDashboard";
import PrescriptionQueue from "./pages/pharmacy/PrescriptionQueue";

import AdminDashboard from "./pages/admin/AdminDashboard";
import DatasetPreparation from "./pages/admin/DatasetPreparation";
import AccessRequests from "./pages/admin/AccessRequests";

import ResearcherDashboard from "./pages/researcher/ResearcherDashboard";
import RequestAccess from "./pages/researcher/RequestAccess";
import MyRequests from "./pages/researcher/MyRequests";

function RootRedirect() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <Navigate to={HOME_ROUTE_BY_ROLE[user.role] || "/login"} replace />;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<RootRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/unauthorized" element={<UnauthorizedPage />} />

      {/* Patient */}
      <Route
        path="/patient"
        element={<ProtectedRoute allowedRoles={["patient"]}><AppLayout /></ProtectedRoute>}
      >
        <Route index element={<PatientDashboard />} />
        <Route path="book-appointment" element={<BookAppointment />} />
        <Route path="appointments" element={<MyAppointments />} />
        <Route path="medical-reports" element={<MedicalReports />} />
        <Route path="billing" element={<Billing />} />
      </Route>

      {/* Doctor */}
      <Route
        path="/doctor"
        element={<ProtectedRoute allowedRoles={["doctor"]}><AppLayout /></ProtectedRoute>}
      >
        <Route index element={<DoctorDashboard />} />
        <Route path="consultations" element={<Consultations />} />
        <Route path="consultations/:appointmentId" element={<ConsultationDetail />} />
        <Route path="patient-records" element={<PatientRecords />} />
        <Route path="prescriptions" element={<Prescriptions />} />
      </Route>

      {/* Lab Technician */}
      <Route
        path="/lab"
        element={<ProtectedRoute allowedRoles={["lab_technician"]}><AppLayout /></ProtectedRoute>}
      >
        <Route index element={<LabDashboard />} />
        <Route path="test-queue" element={<LabTestQueue />} />
      </Route>

      {/* Pharmacist */}
      <Route
        path="/pharmacy"
        element={<ProtectedRoute allowedRoles={["pharmacist"]}><AppLayout /></ProtectedRoute>}
      >
        <Route index element={<PharmacyDashboard />} />
        <Route path="queue" element={<PrescriptionQueue />} />
      </Route>

      {/* Admin */}
      <Route
        path="/admin"
        element={<ProtectedRoute allowedRoles={["admin"]}><AppLayout /></ProtectedRoute>}
      >
        <Route index element={<AdminDashboard />} />
        <Route path="dataset-preparation" element={<DatasetPreparation />} />
        <Route path="access-requests" element={<AccessRequests />} />
      </Route>

      {/* Researcher */}
      <Route
        path="/researcher"
        element={<ProtectedRoute allowedRoles={["researcher"]}><AppLayout /></ProtectedRoute>}
      >
        <Route index element={<ResearcherDashboard />} />
        <Route path="request-access" element={<RequestAccess />} />
        <Route path="my-requests" element={<MyRequests />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <AppRoutes />
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
