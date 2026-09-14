// Maps to the AuditLog class -- written whenever a sensitive action occurs
// (medical record update, prescription change, dataset access decision).
export const auditLogs = [
  { id: "AL-1", action: "Access request approved", detail: "DR-9001 - Outpatient records - Q3 batch", actor: "Priya Iyer (Admin)", timestamp: "2026-09-02 14:20" },
  { id: "AL-2", action: "Access request rejected", detail: "DR-9003 - Outpatient records - Q3 batch", actor: "Priya Iyer (Admin)", timestamp: "2026-09-07 09:05" },
  { id: "AL-3", action: "Medical record updated", detail: "MR-4002 - Rakesh Bhatt", actor: "Dr. Kavya Suresh", timestamp: "2026-09-05 11:40" },
  { id: "AL-4", action: "Synthetic dataset generated", detail: "SD-8002 - 6,500 records", actor: "Synthetix engine", timestamp: "2026-08-20 03:10" },
];
