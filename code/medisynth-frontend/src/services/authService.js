import { users } from "../data/users";
import { simulateRequest, http, USE_MOCKS, getToken, setToken, ApiError } from "./api";

// Backend roles are PATIENT | DOCTOR | ADMIN. The UI routes on lower-case values.
export function normalizeUser(u) {
  return { id: u.id, name: u.name, email: u.email, role: String(u.role).toLowerCase() };
}

export async function login(email, password) {
  if (USE_MOCKS) {
    const user = users.find((u) => u.email.toLowerCase() === email.toLowerCase());
    if (!user) {
      await simulateRequest(null, { delay: 500 });
      throw new Error("No account found with that email.");
    }
    return simulateRequest(user, { delay: 500 });
  }
  const res = await http("/api/auth/login", { method: "POST", body: { email: email.trim(), password }, auth: false });
  setToken(res.accessToken);
  return normalizeUser(res.user); // role comes from the server response, never from the form
}

export async function loginAsDemoRole(userId) {
  if (!USE_MOCKS) throw new Error("Demo roles are only available in mock mode.");
  return simulateRequest(users.find((u) => u.id === userId), { delay: 300 });
}

/** Re-validates a stored token with the server on page load. Resolves null if there is no valid session. */
export async function restoreSession() {
  if (USE_MOCKS || !getToken()) return null;
  try {
    return normalizeUser(await http("/api/auth/me"));
  } catch (err) {
    if (err instanceof ApiError && err.status === 0) throw err; // server down: keep token, let caller decide
    setToken(null);
    return null;
  }
}

/**
 * Registers a PATIENT (returns outcome ACTIVE + signs in) or a DOCTOR
 * (outcome PENDING_APPROVAL, no token, one-time registrationReference).
 * Role/status are decided by the server; we only send the *requested* role.
 */
export async function register(form) {
  if (USE_MOCKS) {
    await simulateRequest(null, { delay: 600 });
    if (form.role !== "PATIENT") {
      return { outcome: "PENDING_APPROVAL", message: "Mock: request submitted.", registrationReference: "mock-reference-" + Math.random().toString(36).slice(2, 12) };
    }
    return { outcome: "ACTIVE", message: "Mock: registered.", user: { id: "U-1001", role: "patient", name: form.name, email: form.email } };
  }
  const body = {
    name: form.name.trim(), email: form.email.trim(), password: form.password, phone: form.phone.trim(),
    role: form.role,
  };
  if (form.role === "DOCTOR") {
    body.specialization = form.specialization.trim();
    body.qualifications = form.qualifications.trim();
    body.experienceYears = Number(form.experienceYears);
    body.licenseNumber = form.licenseNumber.trim();
    if (form.consultationFee !== "") body.consultationFee = Number(form.consultationFee);
    if (form.availability.trim()) body.availability = form.availability.trim();
  }
  if (form.role === "LAB_TECHNICIAN") {
    body.department = form.department.trim();
    body.employeeId = form.employeeId.trim();
  }
  if (form.role === "RESEARCHER") {
    body.institution = form.institution.trim();
    body.researchArea = form.researchArea.trim();
  }
  const res = await http("/api/auth/register", { method: "POST", body, auth: false });
  if (res.outcome === "ACTIVE" && res.accessToken) {
    setToken(res.accessToken);
    return { ...res, accessToken: undefined, user: normalizeUser(res.user) };
  }
  return res;
}

export async function getRegistrationStatus(reference) {
  if (USE_MOCKS) {
    return simulateRequest({ status: "PENDING_APPROVAL", requestedRole: "DOCTOR", message: "Mock: your request is awaiting admin review. You cannot log in yet." });
  }
  return http("/api/auth/registration-status", { query: { reference: reference.trim() }, auth: false });
}

/**
 * "I am a developer and want to create an admin account."
 * The secret is typed by the developer at runtime and sent ONLY in the
 * X-Bootstrap-Secret header. It is never stored, logged or kept in state after submit.
 * Resolves only if the server confirmed (HTTP 201).
 */
export async function bootstrapAdmin(secret, { name, email, phone, password }) {
  if (USE_MOCKS) throw new Error("Admin bootstrap needs the real backend. Set VITE_USE_MOCKS=false.");
  return http("/api/auth/admin/bootstrap", {
    method: "POST",
    auth: false,
    headers: { "X-Bootstrap-Secret": secret },
    body: { name: name.trim(), email: email.trim(), phone: phone.trim(), password },
  });
}

export function logout() {
  setToken(null); // JWTs are stateless; dropping the token ends the session client-side
  return Promise.resolve(true);
}
