import { users } from "../data/users";
import { simulateRequest } from "./api";

// Mock auth only. Real implementation will POST to /api/auth/login and
// receive a JWT + role from the Spring Boot backend.
export async function login(email, _password) {
  const user = users.find((u) => u.email.toLowerCase() === email.toLowerCase());
  if (!user) {
    await simulateRequest(null, { delay: 500 });
    throw new Error("No account found with that email.");
  }
  return simulateRequest(user, { delay: 500 });
}

export async function loginAsDemoRole(userId) {
  const user = users.find((u) => u.id === userId);
  return simulateRequest(user, { delay: 300 });
}

export function logout() {
  // Placeholder for future: invalidate token server-side.
  return Promise.resolve(true);
}
