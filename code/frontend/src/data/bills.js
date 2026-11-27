// Maps to Bill and Payment classes (Appointment *-- Bill *-- Payment) and the
// "Generate bill / Patient makes payment / Payment successful?" flow in the Activity Diagram.
export const bills = [
  { id: "B-7001", appointmentId: "A-30004", patientName: "Anita Verma", amount: 1450, status: "paid", paymentMethod: "UPI", paidOn: "2026-09-10" },
  { id: "B-7002", appointmentId: "A-30001", patientName: "Anita Verma", amount: 900, status: "pending", paymentMethod: null, paidOn: null },
  { id: "B-7003", appointmentId: "A-30002", patientName: "Rakesh Bhatt", amount: 2100, status: "failed", paymentMethod: "Card", paidOn: null },
];
