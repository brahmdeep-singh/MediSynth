import { http, simulateRequest, USE_MOCKS } from "./api";

// Registration-request review (doctor approvals). Always fetched from the backend;
// nothing here is hardcoded in real mode.
let mockRequests = [
  { id: 1, applicantName: "Dr. Neha Kapoor", applicantEmail: "neha.kapoor@example.com", applicantPhone: "+919800000001", requestedRole: "DOCTOR", status: "PENDING_APPROVAL", submittedAt: new Date().toISOString(), doctorProfile: { specialization: "Dermatology", qualifications: "MBBS, MD", experienceYears: 6, licenseNumber: "MOCK-1" } },
];

export async function getRegistrationRequests({ status } = {}) {
  if (USE_MOCKS) return simulateRequest(mockRequests.filter((r) => !status || r.status === status));
  const page = await http("/api/admin/registration-requests", { query: { status, size: 100 } });
  return page.content;
}

export async function approveRegistration(id) {
  if (USE_MOCKS) {
    mockRequests = mockRequests.map((r) => (r.id === id ? { ...r, status: "APPROVED" } : r));
    return simulateRequest(true);
  }
  return http(`/api/admin/registration-requests/${id}/approve`, { method: "PATCH" });
}

export async function rejectRegistration(id, reason) {
  if (USE_MOCKS) {
    mockRequests = mockRequests.map((r) => (r.id === id ? { ...r, status: "REJECTED", rejectionReason: reason } : r));
    return simulateRequest(true);
  }
  return http(`/api/admin/registration-requests/${id}/reject`, { method: "PATCH", body: reason ? { reason } : {} });
}

export async function countPendingRegistrations() {
  if (USE_MOCKS) return mockRequests.filter((r) => r.status === "PENDING_APPROVAL").length;
  const page = await http("/api/admin/registration-requests", { query: { status: "PENDING_APPROVAL", size: 1 } });
  return page.totalElements;
}
