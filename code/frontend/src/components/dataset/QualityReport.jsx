import Alert from "../common/Alert";

const pct = (v) => `${(v * 100).toFixed(1)}%`;

function Metric({ label, value, good = 0.9, warn = 0.75 }) {
  const cls = value >= good ? "" : value >= warn ? "warn" : "bad";
  return (
    <div className="metric-row">
      <span>{label}</span>
      <div className="metric-bar" role="img" aria-label={`${label} ${pct(value)}`}>
        <div className={`metric-fill ${cls}`} style={{ width: `${Math.max(0, Math.min(1, value)) * 100}%` }} />
      </div>
      <span className="metric-value">{pct(value)}</span>
    </div>
  );
}

// Shows the evaluation that decides "meets quality and privacy requirements?".
export default function QualityReport({ report }) {
  if (!report) {
    return <Alert variant="info">This dataset has not been evaluated yet.</Alert>;
  }
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-4)", marginBottom: "var(--sp-4)", flexWrap: "wrap" }}>
        <div>
          <div className="dataset-meta">Overall score</div>
          <div className="score-big">{report.overallScore.toFixed(3)}</div>
        </div>
        <div style={{ flex: 1, minWidth: 220 }}>
          <Alert variant={report.passed ? "success" : "warning"}>
            {report.passed
              ? "Meets the quality and privacy requirements. Researchers can request this dataset."
              : "Does not meet the requirements yet. Regenerate it (a larger record count usually helps)."}
          </Alert>
        </div>
      </div>

      <h4 style={{ margin: "var(--sp-4) 0 var(--sp-2)" }}>Scores</h4>
      <Metric label="Column fidelity" value={report.marginalScore} />
      <Metric label="Relationships" value={report.relationshipScore} />
      <Metric label="Clinical validity" value={report.validityScore} good={0.999} warn={0.99} />
      <Metric label="Privacy" value={report.privacyScore} good={0.95} warn={0.8} />
      <p className="dataset-meta" style={{ marginTop: "var(--sp-2)" }}>
        Privacy: {pct(report.kAnonymityShare)} of records sit in a group of at least {report.k} look-alikes (age band, gender, diagnosis);
        smallest group {report.minClassSize}; {pct(report.uniqueShare)} unique.
      </p>

      {report.failureReasons.length > 0 && (
        <div style={{ marginTop: "var(--sp-4)" }}>
          <Alert variant="warning">
            <strong>Why it did not pass</strong>
            <ul style={{ margin: "var(--sp-1) 0 0", paddingLeft: "1.2em" }}>
              {report.failureReasons.map((r) => <li key={r}>{r}</li>)}
            </ul>
          </Alert>
        </div>
      )}

      <h4 style={{ margin: "var(--sp-5) 0 var(--sp-2)" }}>Clinical relationships preserved</h4>
      <ul className="check-list">
        {report.checks.map((c) => (
          <li key={c.key}>
            <span className={c.passed ? "check-ok" : "check-bad"} aria-label={c.passed ? "passed" : "failed"}>{c.passed ? "✓" : "✗"}</span>
            <span style={{ flex: 1 }}>{c.label}</span>
            <span className="mono dataset-meta">{c.observed.toFixed(2)} (need {c.threshold})</span>
          </li>
        ))}
      </ul>

      <h4 style={{ margin: "var(--sp-5) 0 var(--sp-2)" }}>Column-by-column fidelity</h4>
      <div className="preview-scroll">
        <table className="preview-table">
          <thead><tr><th>Column</th><th>Type</th><th>Score</th><th>Detail</th></tr></thead>
          <tbody>
            {report.columns.map((c) => (
              <tr key={c.column}><td>{c.column}</td><td>{c.kind}</td><td>{c.score.toFixed(3)}</td><td>{c.detail}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
