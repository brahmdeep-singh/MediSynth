// Maps to the Prescription class, aggregating Medicine items (Class Diagram: Prescription o-- Medicine),
// and the "Is medicine required?" branch of the Activity Diagram / Pharmacist swimlane.
export const prescriptions = [
  {
    id: "RX-6001",
    recordId: "MR-4001",
    patientName: "Anita Verma",
    prescribedBy: "Dr. Rohan Mehta",
    dateIssued: "2026-09-10",
    status: "dispensed",
    medicines: [
      { name: "Cetirizine 10mg", dosage: "Once daily, night" },
      { name: "Fluticasone nasal spray", dosage: "Twice daily" },
    ],
  },
  {
    id: "RX-6002",
    recordId: "MR-4002",
    patientName: "Rakesh Bhatt",
    prescribedBy: "Dr. Kavya Suresh",
    dateIssued: "2026-09-05",
    status: "pending",
    medicines: [
      { name: "Amlodipine 5mg", dosage: "Once daily, morning" },
    ],
  },
];
