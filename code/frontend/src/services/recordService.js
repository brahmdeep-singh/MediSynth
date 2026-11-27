import { medicalRecords as seedRecords } from "../data/medicalRecords";
import { simulateRequest } from "./api";

let medicalRecords = [...seedRecords];

export function getMedicalRecords({ patientId } = {}) {
  let list = medicalRecords;
  if (patientId) list = list.filter((r) => r.patientId === patientId);
  return simulateRequest(list);
}

export function getMedicalRecordById(id) {
  return simulateRequest(medicalRecords.find((r) => r.id === id) || null);
}

export function getOrCreateMedicalRecordForPatient(patientId, patientName) {
  let record = medicalRecords.find((r) => r.patientId === patientId);
  if (!record) {
    record = {
      id: `MR-${Math.floor(4000 + Math.random() * 900)}`,
      patientId,
      patientName,
      diagnosis: "Pending consultation",
      notes: "",
      updatedBy: "-",
      updatedAt: "-",
    };
    medicalRecords = [record, ...medicalRecords];
  }
  return simulateRequest(record);
}

export function updateMedicalRecord(id, { diagnosis, notes, updatedBy }) {
  medicalRecords = medicalRecords.map((r) =>
    r.id === id
      ? { ...r, diagnosis, notes, updatedBy, updatedAt: new Date().toISOString().slice(0, 10) }
      : r
  );
  return simulateRequest(true, { delay: 500 });
}
