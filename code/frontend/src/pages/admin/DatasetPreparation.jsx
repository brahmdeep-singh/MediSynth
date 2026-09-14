import { useEffect, useState } from "react";
import { Database, RotateCcw } from "lucide-react";
import Card from "../../components/common/Card";
import Button from "../../components/common/Button";
import FormField from "../../components/common/FormField";
import Input from "../../components/common/Input";
import RecordCard from "../../components/common/RecordCard";
import StatusBadge from "../../components/common/Badge";
import Breadcrumb from "../../components/common/Breadcrumb";
import { SkeletonTable } from "../../components/common/Skeleton";
import { useToast } from "../../context/ToastContext";
import * as datasetService from "../../services/datasetService";

// Implements the Synthetix half of the Activity Diagram:
// Select/prepare dataset -> send to Synthetix -> generate -> evaluate ->
// [meets requirements?] -> Yes: ready / No: regenerate (loops back to evaluate).
export default function DatasetPreparation() {
  const { showToast } = useToast();
  const [datasets, setDatasets] = useState(null);
  const [name, setName] = useState("");
  const [recordCount, setRecordCount] = useState(5000);
  const [generating, setGenerating] = useState(false);
  const [evaluatingId, setEvaluatingId] = useState(null);

  function load() {
    datasetService.getDatasets().then(setDatasets);
  }
  useEffect(load, []);

  async function handleGenerate(e) {
    e.preventDefault();
    setGenerating(true);
    await datasetService.prepareAndGenerateDataset({ name, recordCount });
    setGenerating(false);
    setName("");
    showToast("Synthetix is generating the dataset.", "info");
    load();
  }

  async function handleEvaluate(id) {
    setEvaluatingId(id);
    const { passed, score } = await datasetService.evaluateDataset(id);
    setEvaluatingId(null);
    showToast(
      passed ? `Dataset passed evaluation (score ${score}).` : `Dataset did not meet the quality threshold (score ${score}). Regeneration needed.`,
      passed ? "success" : "danger"
    );
    load();
  }

  async function handleRegenerate(id) {
    setEvaluatingId(id);
    await datasetService.regenerateDataset(id);
    showToast("Regenerating dataset with adjusted parameters...", "info");
    // Auto-evaluate after regeneration, matching the Activity Diagram's loop back to "Evaluate synthetic dataset".
    const { passed, score } = await datasetService.evaluateDataset(id);
    setEvaluatingId(null);
    showToast(passed ? `Regenerated dataset passed (score ${score}).` : `Still below threshold (score ${score}).`, passed ? "success" : "danger");
    load();
  }

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/admin" }, { label: "Dataset preparation" }]} />
      <div className="page-header"><div><h1>Dataset preparation</h1><p>Prepare a healthcare dataset and send it to Synthetix for synthetic generation.</p></div></div>

      <Card title="Generate a new synthetic dataset" className="section">
        <form onSubmit={handleGenerate}>
          <div className="form-grid">
            <FormField label="Dataset name" required htmlFor="name">
              <Input id="name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Outpatient records - Q4 batch" />
            </FormField>
            <FormField label="Target record count" required htmlFor="recordCount">
              <Input id="recordCount" type="number" min="100" step="100" value={recordCount} onChange={(e) => setRecordCount(e.target.value)} />
            </FormField>
          </div>
          <Button type="submit" icon={Database} loading={generating} disabled={!name}>Send to Synthetix</Button>
        </form>
      </Card>

      <h3>Datasets</h3>
      {datasets === null ? (
        <SkeletonTable rows={3} />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-3)" }}>
          {datasets.map((d) => (
            <RecordCard key={d.id} status={d.status === "needs_regeneration" ? "rejected" : d.status}>
              <div style={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: "var(--sp-3)" }}>
                <div>
                  <div style={{ fontWeight: 700 }}>{d.name}</div>
                  <div style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)" }}>
                    <span className="mono">{d.id}</span> &middot; {d.recordCount.toLocaleString()} records &middot; generated {d.generatedDate}
                    {d.qualityScore != null && <> &middot; quality score {d.qualityScore}</>}
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-3)" }}>
                  <StatusBadge status={d.status} />
                  {d.status === "evaluating" && (
                    <Button size="sm" onClick={() => handleEvaluate(d.id)} loading={evaluatingId === d.id}>Evaluate</Button>
                  )}
                  {d.status === "needs_regeneration" && (
                    <Button size="sm" variant="secondary" icon={RotateCcw} onClick={() => handleRegenerate(d.id)} loading={evaluatingId === d.id}>
                      Regenerate
                    </Button>
                  )}
                </div>
              </div>
            </RecordCard>
          ))}
        </div>
      )}
    </div>
  );
}
