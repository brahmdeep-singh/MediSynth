// Navigation is derived directly from the Use Case Diagram: every entry here
// corresponds to a use case owned by that actor (Patient / Doctor / Lab
// Technician / Pharmacist / Admin / Researcher). No item exists here that
// isn't traceable to a use case oval connected to that actor.
import {
  LayoutDashboard, CalendarPlus, CalendarCheck, FileText, Receipt,
  Stethoscope, ClipboardList, Pill, FlaskConical, Database,
  ShieldCheck, FileSearch,
} from "lucide-react";

export const NAV_BY_ROLE = {
  patient: [
    { to: "/patient", label: "Dashboard", icon: LayoutDashboard, end: true },
    { to: "/patient/book-appointment", label: "Book Appointment", icon: CalendarPlus },
    { to: "/patient/appointments", label: "My Appointments", icon: CalendarCheck },
    { to: "/patient/medical-reports", label: "Medical Reports", icon: FileText },
    { to: "/patient/billing", label: "Billing & Payments", icon: Receipt },
  ],
  doctor: [
    { to: "/doctor", label: "Dashboard", icon: LayoutDashboard, end: true },
    { to: "/doctor/consultations", label: "Consultations", icon: Stethoscope },
    { to: "/doctor/patient-records", label: "Patient Records", icon: ClipboardList },
    { to: "/doctor/prescriptions", label: "Prescriptions", icon: Pill },
  ],
  lab_technician: [
    { to: "/lab", label: "Dashboard", icon: LayoutDashboard, end: true },
    { to: "/lab/test-queue", label: "Lab Test Queue", icon: FlaskConical },
  ],
  pharmacist: [
    { to: "/pharmacy", label: "Dashboard", icon: LayoutDashboard, end: true },
    { to: "/pharmacy/queue", label: "Prescription Queue", icon: Pill },
  ],
  admin: [
    { to: "/admin", label: "Dashboard", icon: LayoutDashboard, end: true },
    { to: "/admin/dataset-preparation", label: "Dataset Preparation", icon: Database },
    { to: "/admin/access-requests", label: "Access Requests", icon: ShieldCheck },
  ],
  researcher: [
    { to: "/researcher", label: "Dashboard", icon: LayoutDashboard, end: true },
    { to: "/researcher/request-access", label: "Request Dataset Access", icon: FileSearch },
    { to: "/researcher/my-requests", label: "My Requests", icon: ClipboardList },
  ],
};

export const HOME_ROUTE_BY_ROLE = {
  patient: "/patient",
  doctor: "/doctor",
  lab_technician: "/lab",
  pharmacist: "/pharmacy",
  admin: "/admin",
  researcher: "/researcher",
};
