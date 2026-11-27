import { patients } from "../data/patients";
import { simulateRequest } from "./api";

export function getPatients() {
  return simulateRequest(patients);
}

export function getPatientById(id) {
  const found = patients.find((p) => p.id === id) || null;
  return simulateRequest(found);
}
