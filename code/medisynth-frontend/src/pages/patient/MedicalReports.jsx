import { useEffect, useState } from "react";
import { FileText } from "lucide-react";
import Card from "../../components/common/Card";
import Breadcrumb from "../../components/common/Breadcrumb";
import EmptyState from "../../components/common/EmptyState";
import { SkeletonTable } from "../../components/common/Skeleton";
import ErrorState from "../../components/common/ErrorState";
import * as recordService from "../../services/recordService";
import * as labService from "../../services/labService";
import StatusBadge from "../../components/common/Badge";
import { USE_MOCKS } from "../../services/api";

export default function MedicalReports() {
  const [records, setRecords] = useState(null);
  const [error, setError] = useState(null);
  const [labTests, setLabTests] = useState([]);

  function load() {
    setError(null);
    setRecords(null);
    recordService.getMedicalRecords({ patientId: "P-1001" })
      .then(setRecords)
      .catch(() => setError("Unable to load your medical reports. Please try again."));
  }

  useEffect(load, []);
  useEffect(() => {
    // real lab results ordered for me by my doctors (demo mode has no patient-specific lab data)
    if (!USE_MOCKS) labService.getMyLabTests().then(setLabTests).catch(() => {});
  }, []);

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/patient" }, { label: "Medical reports" }]} />
      <div className="page-header">
        <div>
          <h1>Medical reports</h1>
          <p>Notes and diagnoses recorded by your doctors during past visits.</p>
        </div>
      </div>

      {labTests.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-4)", marginBottom: "var(--sp-5)" }}>
          <h3 style={{ margin: 0 }}>Lab tests</h3>
          {labTests.map((t) => (
            <Card key={t.id} title={t.testType}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: "var(--sp-3)" }}>
                <p style={{ margin: 0 }}>{t.status === "completed" ? t.result : "Result not available yet."}</p>
                <StatusBadge status={t.status} />
              </div>
              <div style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)", marginTop: "var(--sp-2)" }}>
                Ordered by {t.orderedBy}
              </div>
            </Card>
          ))}
        </div>
      )}

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
