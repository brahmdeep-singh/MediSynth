// Mirrors the Person / HospitalStaff generalization hierarchy from the Class Diagram.
// role values correspond 1:1 to the actors in the Use Case + Swimlane diagrams.
export const users = [
  { id: "U-1001", role: "patient", name: "Anita Verma", email: "anita.verma@example.com" },
  { id: "U-2001", role: "doctor", name: "Dr. Rohan Mehta", email: "rohan.mehta@medisynth.org", specialization: "Internal Medicine" },
  { id: "U-3001", role: "lab_technician", name: "Simran Kaur", email: "simran.kaur@medisynth.org" },
  { id: "U-4001", role: "pharmacist", name: "Arjun Nair", email: "arjun.nair@medisynth.org" },
  { id: "U-5001", role: "admin", name: "Priya Iyer", email: "priya.iyer@medisynth.org" },
  { id: "U-6001", role: "researcher", name: "Dr. Alan Reyes", email: "alan.reyes@research.org", institution: "Northbridge Health Institute" },
];

export const ROLE_LABELS = {
  patient: "Patient",
  doctor: "Doctor",
  lab_technician: "Lab Technician",
  pharmacist: "Pharmacist",
  admin: "Admin",
  researcher: "Researcher",
};
