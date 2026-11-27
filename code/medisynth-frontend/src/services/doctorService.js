import { http, simulateRequest, USE_MOCKS } from "./api";

export async function getMyDoctorProfile() {
  if (USE_MOCKS) return simulateRequest({ specialization: "Internal Medicine" });
  return http("/api/doctors/me");
}
