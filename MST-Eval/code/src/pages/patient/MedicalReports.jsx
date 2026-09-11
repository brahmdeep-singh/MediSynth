import { useEffect, useState } from "react";
import { FileText } from "lucide-react";
import Card from "../../components/common/Card";
import Breadcrumb from "../../components/common/Breadcrumb";
import EmptyState from "../../components/common/EmptyState";
import { SkeletonTable } from "../../components/common/Skeleton";
import ErrorState from "../../components/common/ErrorState";
import * as recordService from "../../services/recordService";

export default function MedicalReports() {
  const [records, setRecords] = useState(null);
  const [error, setError] = useState(null);

  function load() {
    setError(null);
    setRecords(null);
    recordService.getMedicalRecords({ patientId: "P-1001" })
      .then(setRecords)
      .catch(() => setError("Unable to load your medical reports. Please try again."));
  }

  useEffect(load, []);

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/patient" }, { label: "Medical reports" }]} />
      <div className="page-header">
        <div>
          <h1>Medical reports</h1>
          <p>Notes and diagnoses recorded by your doctors during past visits.</p>
        </div>
      </div>

      {records === null ? (
        <SkeletonTable rows={3} />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : records.filter((r) => r.diagnosis && r.diagnosis !== "Pending consultation").length === 0 ? (
        <EmptyState icon={FileText} title="No medical reports yet" message="Reports will appear here after a doctor completes a consultation." />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-4)" }}>
          {records
            .filter((r) => r.diagnosis && r.diagnosis !== "Pending consultation")
            .map((r) => (
              <Card key={r.id} title={r.diagnosis}>
                <p style={{ marginBottom: "var(--sp-2)" }}>{r.notes}</p>
                <div style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)" }}>
                  Recorded by {r.updatedBy} on {r.updatedAt} &middot; <span className="mono">{r.id}</span>
                </div>
              </Card>
            ))}
        </div>
      )}
    </div>
  );
}
