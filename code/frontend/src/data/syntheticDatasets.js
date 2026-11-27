// Mock data for demo mode (VITE_USE_MOCKS=true) only. In real mode datasets come from the backend.
// Maps to the SyntheticDataset class and the Synthetix generation workflow
// (Part 2 of the Activity Diagram / Sequence Diagram 2: Generate Synthetic Dataset).
export const syntheticDatasets = [
  { id: "SD-8001", name: "Outpatient records - Q3 batch", description: "General outpatient visits", generatedDate: "2026-09-01", recordCount: 10000, version: 1, qualityScore: 0.94, status: "ready", report: null },
  { id: "SD-8002", name: "Lab + prescription batch (regenerated)", description: "Labs and medication use", generatedDate: "2026-08-20", recordCount: 6500, version: 2, qualityScore: 0.91, status: "ready", report: null },
  { id: "SD-8003", name: "Billing records batch", description: "", generatedDate: "2026-09-09", recordCount: 4000, version: 1, qualityScore: null, status: "evaluating", report: null },
];
