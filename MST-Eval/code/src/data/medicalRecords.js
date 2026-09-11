// Maps to the MedicalRecord class, composed of a Patient (Class Diagram: Patient *-- MedicalRecord)
// and aggregating LabTest and Prescription records.
export const medicalRecords = [
  {
    id: "MR-4001",
    patientId: "P-1001",
    patientName: "Anita Verma",
    diagnosis: "Seasonal allergic rhinitis",
    notes: "Patient reports recurring nasal congestion in the mornings. No fever. Recommended antihistamine course and follow-up in 2 weeks.",
    updatedBy: "Dr. Rohan Mehta",
    updatedAt: "2026-09-10",
  },
  {
    id: "MR-4002",
    patientId: "P-1002",
    patientName: "Rakesh Bhatt",
    diagnosis: "Hypertension (stage 1)",
    notes: "Blood pressure 142/91 at last visit. Advised dietary sodium reduction and daily walking. Lab panel ordered to rule out secondary causes.",
    updatedBy: "Dr. Kavya Suresh",
    updatedAt: "2026-09-05",
  },
  {
    id: "MR-4003",
    patientId: "P-1003",
    patientName: "Meera Joshi",
    diagnosis: "Pending consultation",
    notes: "",
    updatedBy: "-",
    updatedAt: "-",
  },
];
