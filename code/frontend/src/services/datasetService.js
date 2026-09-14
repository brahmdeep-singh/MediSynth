import { syntheticDatasets as seedDatasets } from "../data/syntheticDatasets";
import { datasetRequests as seedRequests } from "../data/datasetRequests";
import { auditLogs as seedLogs } from "../data/auditLogs";
import { simulateRequest } from "./api";

let datasets = [...seedDatasets];
let requests = [...seedRequests];
let logs = [...seedLogs];

// --- Admin: dataset preparation pipeline (Activity Diagram Part 2 / Sequence Diagram 2) ---
export function getDatasets() {
  return simulateRequest(datasets);
}

export function prepareAndGenerateDataset({ name, recordCount }) {
  const newDataset = {
    id: `SD-${Math.floor(8000 + Math.random() * 900)}`,
    name,
    generatedDate: new Date().toISOString().slice(0, 10),
    recordCount: Number(recordCount) || 5000,
    qualityScore: null,
    status: "evaluating",
  };
  datasets = [newDataset, ...datasets];
  return simulateRequest(newDataset, { delay: 900 });
}

export function evaluateDataset(id) {
  // Simulates the "Meets quality and privacy requirements?" decision.
  const score = Number((0.85 + Math.random() * 0.13).toFixed(2));
  const passed = score >= 0.9;
  datasets = datasets.map((d) =>
    d.id === id ? { ...d, qualityScore: score, status: passed ? "ready" : "needs_regeneration" } : d
  );
  return simulateRequest({ passed, score }, { delay: 700 });
}

export function regenerateDataset(id) {
  datasets = datasets.map((d) => (d.id === id ? { ...d, status: "evaluating" } : d));
  return simulateRequest(true, { delay: 700 });
}

// --- Researcher: request access ---
export function getDatasetRequests({ researcherName, status } = {}) {
  let list = requests;
  if (researcherName) list = list.filter((r) => r.researcherName === researcherName);
  if (status) list = list.filter((r) => r.status === status);
  return simulateRequest(list);
}

export function requestDatasetAccess({ datasetId, datasetName, researcherName, institution, purpose }) {
  const newReq = {
    id: `DR-${Math.floor(9000 + Math.random() * 900)}`,
    datasetId,
    datasetName,
    researcherName,
    institution,
    purpose,
    requestDate: new Date().toISOString().slice(0, 10),
    status: "pending",
  };
  requests = [newReq, ...requests];
  return simulateRequest(newReq, { delay: 600 });
}

// --- Admin: review access requests ---
export function decideDatasetRequest(id, decision, actorName) {
  requests = requests.map((r) => (r.id === id ? { ...r, status: decision } : r));
  const req = requests.find((r) => r.id === id);
  logs = [
    {
      id: `AL-${logs.length + 1}`,
      action: decision === "approved" ? "Access request approved" : "Access request rejected",
      detail: `${id} - ${req?.datasetName ?? ""}`,
      actor: `${actorName} (Admin)`,
      timestamp: new Date().toISOString().slice(0, 16).replace("T", " "),
    },
    ...logs,
  ];
  return simulateRequest(true, { delay: 500 });
}

export function getAuditLogs() {
  return simulateRequest(logs);
}
