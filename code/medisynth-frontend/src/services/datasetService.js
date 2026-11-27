import { syntheticDatasets as seedDatasets } from "../data/syntheticDatasets";
import { datasetRequests as seedRequests } from "../data/datasetRequests";
import { auditLogs as seedLogs } from "../data/auditLogs";
import { simulateRequest, http, downloadFile, saveBlob, USE_MOCKS } from "./api";

// =====================================================================
// Real mode (default): the Synthetix pipeline, the dataset catalogue, access
// requests, admin decisions and the audit log all call the Spring Boot backend.
// Mock mode (VITE_USE_MOCKS=true): the same functions run on in-memory data.
//
// UI shape of a dataset:
//   { id, name, description, recordCount, version, status, qualityScore, generatedDate,
//     createdByName, profileSource, report }
//   status: evaluating | ready | needs_regeneration | retired
//   report: null until evaluated, else
//     { overallScore, marginalScore, relationshipScore, validityScore, privacyScore, k,
//       kAnonymityShare, minClassSize, uniqueShare, passed, columns[], checks[], failureReasons[] }
// =====================================================================

// ===================================================================== mock
let datasets = seedDatasets.map((d) => ({ ...d }));
let requests = [...seedRequests];
let logs = [...seedLogs];

function mockReport(score, passed) {
  const pct = (x) => Number(Math.min(1, x).toFixed(3));
  return {
    overallScore: score, marginalScore: pct(score + 0.02), relationshipScore: 1, validityScore: 1,
    privacyScore: pct(passed ? score : score - 0.2), k: 5, kAnonymityShare: passed ? 0.98 : 0.9, minClassSize: passed ? 3 : 1,
    uniqueShare: passed ? 0.004 : 0.03, passed,
    columns: [{ column: "diagnosis", kind: "categorical", score: pct(score + 0.03), detail: "total variation distance 0.020" }],
    checks: [{ key: "age_sbp", label: "Older patients have higher systolic blood pressure (correlation)", observed: 0.62, threshold: 0.2, passed: true }],
    failureReasons: passed ? [] : ["Demo mode: score below the 0.90 threshold. Regenerate with more records."],
  };
}

const mock = {
  getDatasets: () => simulateRequest(datasets),
  prepareAndGenerateDataset({ name, description, recordCount }) {
    const d = {
      id: `SD-${Math.floor(8000 + Math.random() * 900)}`, name, description: description || "",
      generatedDate: new Date().toISOString().slice(0, 10), recordCount: Number(recordCount) || 5000,
      version: 1, qualityScore: null, status: "evaluating", report: null,
    };
    datasets = [d, ...datasets];
    return simulateRequest(d, { delay: 900 });
  },
  evaluateDataset(id) {
    const score = Number((0.85 + Math.random() * 0.13).toFixed(2));
    const passed = score >= 0.9;
    datasets = datasets.map((d) => d.id === id
      ? { ...d, qualityScore: score, status: passed ? "ready" : "needs_regeneration", report: mockReport(score, passed) } : d);
    return simulateRequest({ passed, score, dataset: datasets.find((d) => d.id === id) }, { delay: 700 });
  },
  regenerateDataset(id, recordCount) {
    datasets = datasets.map((d) => d.id === id
      ? { ...d, status: "evaluating", version: d.version + 1, qualityScore: null, report: null, recordCount: recordCount || d.recordCount } : d);
    return simulateRequest(datasets.find((d) => d.id === id), { delay: 700 });
  },
  retireDataset(id) {
    datasets = datasets.map((d) => (d.id === id ? { ...d, status: "retired" } : d));
    return simulateRequest(true);
  },
  getDatasetSummary() {
    const c = (s) => datasets.filter((d) => d.status === s).length;
    return simulateRequest({ total: datasets.length, ready: c("ready"), evaluating: c("evaluating"), needsRegeneration: c("needs_regeneration"), retired: c("retired") });
  },
  getCatalogue() {
    return simulateRequest(datasets.filter((d) => d.status === "ready").map((d) => {
      const r = requests.find((x) => x.datasetId === d.id && x.researcherName);
      return { id: d.id, name: d.name, description: d.description, recordCount: d.recordCount, version: d.version,
        qualityScore: d.qualityScore, privacyScore: null, k: 5, accessStatus: r ? r.status : null };
    }));
  },
  getDatasetColumns: () => simulateRequest(MOCK_COLUMNS),
  getDatasetPreview: (id, { size = 20 } = {}) => simulateRequest(mockRows(size)),
  async downloadDataset(id) {
    await simulateRequest(true, { delay: 300 });
    saveBlob(new Blob([toCsv(mockRows(200))], { type: "text/csv" }), `demo-dataset-${id}.csv`);
  },
  getDatasetRequests({ researcherName, status } = {}) {
    let list = requests;
    if (researcherName) list = list.filter((r) => r.researcherName === researcherName);
    if (status) list = list.filter((r) => r.status === status);
    return simulateRequest(list);
  },
  requestDatasetAccess({ datasetId, datasetName, researcherName, institution, purpose }) {
    const r = {
      id: `DR-${Math.floor(9000 + Math.random() * 900)}`, datasetId, datasetName, researcherName, institution, purpose,
      requestDate: new Date().toISOString().slice(0, 10), status: "pending",
    };
    requests = [r, ...requests];
    return simulateRequest(r, { delay: 600 });
  },
  decideDatasetRequest(id, decision, actorName) {
    requests = requests.map((r) => (r.id === id ? { ...r, status: decision } : r));
    const req = requests.find((r) => r.id === id);
    logs = [{
      id: `AL-${logs.length + 1}`, action: decision === "approved" ? "Access request approved" : "Access request rejected",
      detail: `${id} - ${req?.datasetName ?? ""}`, actor: `${actorName} (Admin)`,
      timestamp: new Date().toISOString().slice(0, 16).replace("T", " "),
    }, ...logs];
    return simulateRequest(true, { delay: 500 });
  },
  getAuditLogs: () => simulateRequest(logs),
};

const MOCK_COLUMNS = [
  { name: "syntheticId", type: "text", role: "identifier", description: "Random sequence id." },
  { name: "ageYears", type: "integer", role: "quasi-identifier", description: "Age in years (90+ top-coded)." },
  { name: "gender", type: "category", role: "quasi-identifier", description: "MALE, FEMALE or OTHER." },
  { name: "diagnosisCode", type: "category", role: "quasi-identifier", description: "Primary diagnosis (ICD-10)." },
  { name: "systolicBp", type: "integer", role: "attribute", description: "Systolic blood pressure, mmHg." },
];

function mockRows(n) {
  const dx = ["Z00", "J06", "I10", "E11", "M54"];
  return Array.from({ length: n }, (_, i) => ({
    syntheticId: `SYN-${String(1000000 + i).slice(1)}`, ageYears: 18 + ((i * 7) % 70), gender: i % 2 ? "FEMALE" : "MALE",
    diagnosisCode: dx[i % dx.length], systolicBp: 100 + ((i * 11) % 60),
  }));
}

function toCsv(rows) {
  if (!rows.length) return "";
  const cols = Object.keys(rows[0]);
  return [cols.join(","), ...rows.map((r) => cols.map((c) => r[c]).join(","))].join("\n") + "\n";
}

// ===================================================================== real backend
const DATASET_STATUS = { EVALUATING: "evaluating", READY: "ready", NEEDS_REGENERATION: "needs_regeneration", RETIRED: "retired" };
const REQ_STATUS = { PENDING_APPROVAL: "pending", APPROVED: "approved", REJECTED: "rejected" };

function datasetFromApi(d) {
  return {
    id: d.id, name: d.name, description: d.description || "", recordCount: d.recordCount, version: d.version,
    status: DATASET_STATUS[d.status] || String(d.status).toLowerCase(),
    qualityScore: d.qualityScore != null ? Number(d.qualityScore.toFixed(3)) : null,
    generatedDate: d.createdAt ? new Date(d.createdAt).toISOString().slice(0, 10) : "",
    createdByName: d.createdByName, profileSource: d.profileSource, profileSampleBucket: d.profileSampleBucket,
    report: d.report || null,
  };
}

function catalogueItemFromApi(i) {
  return {
    id: i.id, name: i.name, description: i.description || "", recordCount: i.recordCount, version: i.version,
    qualityScore: i.qualityScore != null ? Number(i.qualityScore.toFixed(3)) : null, privacyScore: i.privacyScore, k: i.k,
    accessStatus: i.accessStatus ? REQ_STATUS[i.accessStatus] : null, accessRequestId: i.accessRequestId,
  };
}

function reqFromApi(r) {
  return {
    id: r.id, datasetId: r.datasetId, datasetName: r.datasetTitle, researcherName: r.researcherName,
    institution: r.researcherInstitution, purpose: r.purpose, dataScope: r.dataScope, decisionNote: r.decisionNote,
    requestDate: new Date(r.createdAt).toISOString().slice(0, 10),
    status: REQ_STATUS[r.status] || String(r.status).toLowerCase(),
  };
}

const real = {
  async getDatasets() {
    const page = await http("/api/admin/datasets", { query: { size: 100 } });
    return page.content.map(datasetFromApi);
  },
  async prepareAndGenerateDataset({ name, description, recordCount }) {
    return datasetFromApi(await http("/api/admin/datasets", {
      method: "POST",
      body: { name: name.trim(), description: description?.trim() || undefined, recordCount: Number(recordCount) },
    }));
  },
  async evaluateDataset(id) {
    const d = datasetFromApi(await http(`/api/admin/datasets/${id}/evaluate`, { method: "POST", body: {} }));
    return { passed: d.status === "ready", score: d.qualityScore, dataset: d };
  },
  async regenerateDataset(id, recordCount) {
    return datasetFromApi(await http(`/api/admin/datasets/${id}/regenerate`, {
      method: "POST", body: recordCount ? { recordCount: Number(recordCount) } : {},
    }));
  },
  async retireDataset(id) {
    await http(`/api/admin/datasets/${id}/retire`, { method: "POST", body: {} });
    return true;
  },
  getDatasetSummary: () => http("/api/admin/datasets/summary"),
  async getCatalogue() {
    const page = await http("/api/research/datasets", { query: { size: 100 } });
    return page.content.map(catalogueItemFromApi);
  },
  getDatasetColumns: () => http("/api/research/datasets/columns"),
  getDatasetPreview: (id, { admin = false, size = 20 } = {}) =>
    http(`/api/${admin ? "admin" : "research"}/datasets/${id}/preview`, { query: { size } }),
  downloadDataset: (id, { admin = false } = {}) =>
    downloadFile(`/api/${admin ? "admin" : "research"}/datasets/${id}/download`, `synthetix-dataset-${id}.csv`),

  // researcherName given -> the signed-in researcher's own requests; otherwise the admin review list
  async getDatasetRequests({ researcherName } = {}) {
    const path = researcherName ? "/api/research/requests/my" : "/api/admin/research-requests";
    const page = await http(path, { query: { size: 100 } });
    return page.content.map(reqFromApi);
  },
  async requestDatasetAccess({ datasetId, datasetName, purpose, dataScope }) {
    const body = { purpose: purpose.trim(), dataScope: dataScope?.trim() || undefined };
    if (datasetId) body.datasetId = Number(datasetId);
    else body.datasetTitle = datasetName.trim();
    return reqFromApi(await http("/api/research/requests", { method: "POST", body }));
  },
  async decideDatasetRequest(id, decision) {
    await http(`/api/admin/research-requests/${id}/${decision === "approved" ? "approve" : "reject"}`, { method: "PATCH", body: {} });
    return true;
  },
  async getAuditLogs() {
    const page = await http("/api/admin/audit-logs", { query: { size: 50 } });
    return page.content.map((l) => ({
      id: l.id,
      action: String(l.action).replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase()),
      detail: [l.targetType && `${l.targetType} #${l.targetId ?? ""}`, l.metadata].filter(Boolean).join(" - ") || "-",
      actor: l.actorName || "System",
      timestamp: new Date(l.occurredAt).toLocaleString(),
    }));
  },
};

const impl = USE_MOCKS ? mock : real;
// Admin: Synthetix pipeline
export const getDatasets = (...a) => impl.getDatasets(...a);
export const prepareAndGenerateDataset = (...a) => impl.prepareAndGenerateDataset(...a);
export const evaluateDataset = (...a) => impl.evaluateDataset(...a);
export const regenerateDataset = (...a) => impl.regenerateDataset(...a);
export const retireDataset = (...a) => impl.retireDataset(...a);
export const getDatasetSummary = (...a) => impl.getDatasetSummary(...a);
// Researcher: catalogue and rows (admin passes { admin: true } to preview/download any dataset)
export const getCatalogue = (...a) => impl.getCatalogue(...a);
export const getDatasetColumns = (...a) => impl.getDatasetColumns(...a);
export const getDatasetPreview = (...a) => impl.getDatasetPreview(...a);
export const downloadDataset = (...a) => impl.downloadDataset(...a);
// Access requests and audit
export const getDatasetRequests = (...a) => impl.getDatasetRequests(...a);
export const requestDatasetAccess = (...a) => impl.requestDatasetAccess(...a);
export const decideDatasetRequest = (...a) => impl.decideDatasetRequest(...a);
export const getAuditLogs = (...a) => impl.getAuditLogs(...a);
