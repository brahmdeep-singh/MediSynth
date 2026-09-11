// Maps to the DatasetRequest class and the Researcher/Admin swimlane
// (request access -> admin review -> approve/reject -> provide dataset + audit log).
export const datasetRequests = [
  { id: "DR-9001", datasetId: "SD-8001", datasetName: "Outpatient records - Q3 batch", researcherName: "Dr. Alan Reyes", institution: "Northbridge Health Institute", requestDate: "2026-09-02", status: "approved", purpose: "Benchmarking a readmission-risk model." },
  { id: "DR-9002", datasetId: "SD-8002", datasetName: "Lab + prescription batch (regenerated)", researcherName: "Dr. Alan Reyes", institution: "Northbridge Health Institute", requestDate: "2026-09-08", status: "pending", purpose: "Validating lab-value distribution assumptions." },
  { id: "DR-9003", datasetId: "SD-8001", datasetName: "Outpatient records - Q3 batch", researcherName: "Dr. Wren Okafor", institution: "Delta Analytics Lab", requestDate: "2026-09-07", status: "rejected", purpose: "Commercial product testing (outside approved use policy)." },
];
