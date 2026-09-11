import { labTests as seedTests } from "../data/labTests";
import { simulateRequest } from "./api";

let labTests = [...seedTests];

export function getLabTests({ status } = {}) {
  let list = labTests;
  if (status) list = list.filter((t) => t.status === status);
  return simulateRequest(list);
}

export function orderLabTest({ recordId, patientName, testType, orderedBy }) {
  const newTest = {
    id: `LT-${Math.floor(5000 + Math.random() * 900)}`,
    recordId,
    patientName,
    testType,
    orderedBy,
    status: "pending",
    result: null,
  };
  labTests = [newTest, ...labTests];
  return simulateRequest(newTest, { delay: 500 });
}

export function submitLabResult(id, result) {
  labTests = labTests.map((t) => (t.id === id ? { ...t, status: "completed", result } : t));
  return simulateRequest(true, { delay: 500 });
}
