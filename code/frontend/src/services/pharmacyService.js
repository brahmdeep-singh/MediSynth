import { prescriptions as seedPrescriptions } from "../data/prescriptions";
import { simulateRequest } from "./api";

let prescriptions = [...seedPrescriptions];

export function getPrescriptions({ status } = {}) {
  let list = prescriptions;
  if (status) list = list.filter((p) => p.status === status);
  return simulateRequest(list);
}

export function createPrescription({ recordId, patientName, prescribedBy, medicines }) {
  const newRx = {
    id: `RX-${Math.floor(6000 + Math.random() * 900)}`,
    recordId,
    patientName,
    prescribedBy,
    dateIssued: new Date().toISOString().slice(0, 10),
    status: "pending",
    medicines,
  };
  prescriptions = [newRx, ...prescriptions];
  return simulateRequest(newRx, { delay: 500 });
}

export function dispenseMedicine(id) {
  prescriptions = prescriptions.map((p) => (p.id === id ? { ...p, status: "dispensed" } : p));
  return simulateRequest(true, { delay: 500 });
}
