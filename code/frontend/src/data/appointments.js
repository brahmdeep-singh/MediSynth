// Maps to the Appointment class (appointmentId, date, timeSlot, status)
// and the "Book appointment" / "Manage Appointments" flows in the Activity + DFD diagrams.
export const appointments = [
  { id: "A-30001", patientId: "P-1001", patientName: "Anita Verma", doctorId: "U-2001", doctorName: "Dr. Rohan Mehta", date: "2026-09-15", timeSlot: "10:30 AM", status: "confirmed" },
  { id: "A-30002", patientId: "P-1002", patientName: "Rakesh Bhatt", doctorId: "U-2002", doctorName: "Dr. Kavya Suresh", date: "2026-09-15", timeSlot: "11:15 AM", status: "confirmed" },
  { id: "A-30003", patientId: "P-1003", patientName: "Meera Joshi", doctorId: "U-2001", doctorName: "Dr. Rohan Mehta", date: "2026-09-16", timeSlot: "09:00 AM", status: "pending" },
  { id: "A-30004", patientId: "P-1001", patientName: "Anita Verma", doctorId: "U-2003", doctorName: "Dr. Imran Siddiqui", date: "2026-09-10", timeSlot: "02:00 PM", status: "completed" },
  { id: "A-30005", patientId: "P-1004", patientName: "Sameer Khan", doctorId: "U-2004", doctorName: "Dr. Leela Menon", date: "2026-09-08", timeSlot: "04:30 PM", status: "cancelled" },
];
