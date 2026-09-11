// Maps to the SyntheticDataset class and the Synthetix generation workflow
// (Part 2 of the Activity Diagram / Sequence Diagram 2: Generate Synthetic Dataset).
export const syntheticDatasets = [
  {
    id: "SD-8001",
    name: "Outpatient records - Q3 batch",
    generatedDate: "2026-09-01",
    recordCount: 10000,
    qualityScore: 0.94,
    status: "ready",
  },
  {
    id: "SD-8002",
    name: "Lab + prescription batch (regenerated)",
    generatedDate: "2026-08-20",
    recordCount: 6500,
    qualityScore: 0.88,
    status: "ready",
  },
  {
    id: "SD-8003",
    name: "Billing records batch",
    generatedDate: "2026-09-09",
    recordCount: 4000,
    qualityScore: null,
    status: "evaluating",
  },
];
