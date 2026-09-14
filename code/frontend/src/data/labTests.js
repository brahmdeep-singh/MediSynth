// Maps to the LabTest class and the "Are lab tests required?" branch of the Activity Diagram,
// plus the Lab Technician swimlane (perform test / update lab report).
export const labTests = [
  { id: "LT-5001", recordId: "MR-4002", patientName: "Rakesh Bhatt", testType: "Lipid Profile", orderedBy: "Dr. Kavya Suresh", status: "pending", result: null },
  { id: "LT-5002", recordId: "MR-4002", patientName: "Rakesh Bhatt", testType: "Kidney Function Test", orderedBy: "Dr. Kavya Suresh", status: "pending", result: null },
  { id: "LT-5003", recordId: "MR-4001", patientName: "Anita Verma", testType: "Complete Blood Count", orderedBy: "Dr. Rohan Mehta", status: "completed", result: "Within normal range." },
];
