import { labTests as seedTests } from "../data/labTests";
import { simulateRequest, http, USE_MOCKS } from "./api";

// UI status names: pending | processing | completed | cancelled  (backend: REQUESTED | IN_PROGRESS | COMPLETED | CANCELLED)
const STATUS_TO_UI = { REQUESTED: "pending", IN_PROGRESS: "processing", COMPLETED: "completed", CANCELLED: "cancelled" };
const UI_TO_STATUS = { pending: "REQUESTED", processing: "IN_PROGRESS", completed: "COMPLETED", cancelled: "CANCELLED" };

function fromApi(t) {
  return {
    id: t.id,
    appointmentId: t.appointmentId,
    patientName: t.patientName,
    testType: t.testType,
    notes: t.clinicalNotes,
    orderedBy: t.orderedByName,
    status: STATUS_TO_UI[t.status] || String(t.status).toLowerCase(),
    result: t.resultSummary,
    processedById: t.processedById,
    processedByName: t.processedByName,
    createdAt: t.createdAt,
  };
}

// ----------------------------------------------------------------- mock (demo mode)
let labTests = [...seedTests];

const mock = {
  getLabTests({ status } = {}) {
    let list = labTests;
    if (status) list = list.filter((t) => t.status === status);
    return simulateRequest(list);
  },
  getMyLabTests() { return simulateRequest(labTests); },
  orderLabTest({ recordId, patientName, testType, orderedBy }) {
    const newTest = {
      id: `LT-${Math.floor(5000 + Math.random() * 900)}`, recordId, patientName, testType, orderedBy,
      status: "pending", result: null,
    };
    labTests = [newTest, ...labTests];
    return simulateRequest(newTest, { delay: 500 });
  },
  startLabTest(id) {
    labTests = labTests.map((t) => (t.id === id ? { ...t, status: "processing", processedById: "me" } : t));
    return simulateRequest(true, { delay: 300 });
  },
  submitLabResult(id, result) {
    labTests = labTests.map((t) => (t.id === id ? { ...t, status: "completed", result } : t));
    return simulateRequest(true, { delay: 500 });
  },
};

// ----------------------------------------------------------------- real backend
const real = {
  /** Lab technician work queue. */
  async getLabTests({ status } = {}) {
    const page = await http("/api/lab/tests", { query: { status: status ? UI_TO_STATUS[status] : undefined, size: 100 } });
    return page.content.map(fromApi);
  },
  /** Patient: tests ordered for me. Doctor: tests I ordered. */
  async getMyLabTests() {
    const page = await http("/api/lab-tests/my", { query: { size: 100 } });
    return page.content.map(fromApi);
  },
  async orderLabTest({ appointmentId, testType, notes }) {
    return fromApi(await http("/api/lab-tests", { method: "POST", body: { appointmentId: Number(appointmentId), testType: testType.trim(), notes: notes?.trim() || undefined } }));
  },
  async startLabTest(id) {
    await http(`/api/lab/tests/${id}/start`, { method: "PATCH", body: {} });
    return true;
  },
  async submitLabResult(id, result) {
    await http(`/api/lab/tests/${id}/complete`, { method: "PATCH", body: { resultSummary: result.trim() } });
    return true;
  },
};

const impl = USE_MOCKS ? mock : real;
export const getLabTests = (...a) => impl.getLabTests(...a);
export const getMyLabTests = (...a) => impl.getMyLabTests(...a);
export const orderLabTest = (...a) => impl.orderLabTest(...a);
export const startLabTest = (...a) => impl.startLabTest(...a);
export const submitLabResult = (...a) => impl.submitLabResult(...a);
