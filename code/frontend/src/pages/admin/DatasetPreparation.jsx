import { useEffect, useState } from "react";
import { Database, RotateCcw, FileBarChart, Download, Eye, Archive } from "lucide-react";
import Card from "../../components/common/Card";
import Button from "../../components/common/Button";
import FormField from "../../components/common/FormField";
import Input from "../../components/common/Input";
import Alert from "../../components/common/Alert";
import Modal from "../../components/common/Modal";
import ConfirmDialog from "../../components/common/ConfirmDialog";
import RecordCard from "../../components/common/RecordCard";
import StatusBadge from "../../components/common/Badge";
import Breadcrumb from "../../components/common/Breadcrumb";
import EmptyState from "../../components/common/EmptyState";
import { SkeletonTable } from "../../components/common/Skeleton";
import QualityReport from "../../components/dataset/QualityReport";
import RecordsPreview from "../../components/dataset/RecordsPreview";
import { useToast } from "../../context/ToastContext";
import * as datasetService from "../../services/datasetService";

// Implements the Synthetix half of the Activity Diagram:
// Select/prepare dataset -> send to Synthetix -> generate -> evaluate ->
// [meets requirements?] -> Yes: ready / No: regenerate (loops back to evaluate).
export default function DatasetPreparation() {
  const { showToast } = useToast();
  const [datasets, setDatasets] = useState(null);
  const [error, setError] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [recordCount, setRecordCount] = useState(5000);
  const [generating, setGenerating] = useState(false);
  const [busyId, setBusyId] = useState(null);
  const [regen, setRegen] = useState(null);       // { dataset, recordCount }
  const [detail, setDetail] = useState(null);     // dataset shown in the report dialog
  const [preview, setPreview] = useState(null);   // rows, loaded on demand
  const [previewing, setPreviewing] = useState(false);
  const [retireTarget, setRetireTarget] = useState(null);

  function load() {
    datasetService.getDatasets().then((d) => { setDatasets(d); setError(""); }).catch((e) => setError(e.message));
  }
  useEffect(load, []);

  async function handleGenerate(e) {
    e.preventDefault();
    setGenerating(true);
    try {
      await datasetService.prepareAndGenerateDataset({ name, description, recordCount });
      setName(""); setDescription("");
      showToast("Synthetix generated the dataset. Evaluate it next.", "success");
      load();
    } catch (err) {
      showToast(err.message, "danger");
    } finally {
      setGenerating(false);
    }
  }

  async function handleEvaluate(id) {
    setBusyId(id);
    try {
      const { passed, score } = await datasetService.evaluateDataset(id);
      showToast(
        passed ? `Dataset passed evaluation (score ${score}). Researchers can now request it.`
          : `Dataset did not meet the requirements (score ${score}). Regeneration needed.`,
        passed ? "success" : "danger");
      load();
    } catch (err) {
      showToast(err.message, "danger");
    } finally {
      setBusyId(null);
    }
  }

  async function handleRegenerate() {
    const { dataset, recordCount: count } = regen;
    setBusyId(dataset.id);
    try {
      await datasetService.regenerateDataset(dataset.id, count === dataset.recordCount ? undefined : count);
      setRegen(null);
      showToast("Regenerated. Evaluate the new version.", "info");
      load();
    } catch (err) {
      showToast(err.message, "danger");
    } finally {
      setBusyId(null);
    }
  }

  async function handleRetire() {
    setBusyId(retireTarget.id);
    try {
      await datasetService.retireDataset(retireTarget.id);
      showToast("Dataset retired. Researcher access has ended.", "info");
      setRetireTarget(null); setDetail(null);
      load();
    } catch (err) {
      showToast(err.message, "danger");
    } finally {
      setBusyId(null);
    }
  }

  function openDetail(d) { setDetail(d); setPreview(null); }

  async function loadPreview() {
    setPreviewing(true);
    try { setPreview(await datasetService.getDatasetPreview(detail.id, { admin: true, size: 15 })); }
    catch (err) { showToast(err.message, "danger"); }
    finally { setPreviewing(false); }
  }

  async function download() {
    try { await datasetService.downloadDataset(detail.id, { admin: true }); }
    catch (err) { showToast(err.message, "danger"); }
  }

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/admin" }, { label: "Dataset preparation" }]} />
      <div className="page-header"><div><h1>Dataset preparation</h1><p>Generate a synthetic healthcare dataset with Synthetix, evaluate it, and publish it for researchers.</p></div></div>

      <Card title="Generate a new synthetic dataset" className="section">
        <form onSubmit={handleGenerate}>
          <div className="form-grid">
            <FormField label="Dataset name" required htmlFor="name">
              <Input id="name" value={name} maxLength={150} onChange={(e) => setName(e.target.value)} placeholder="e.g. Outpatient records - Q4 batch" />
            </FormField>
            <FormField label="Number of records" required htmlFor="recordCount" hint="500 to 50,000. Small datasets usually fail the privacy check; 5,000 or more is recommended.">
              <Input id="recordCount" type="number" min="500" max="50000" step="500" value={recordCount} onChange={(e) => setRecordCount(e.target.value)} />
            </FormField>
          </div>
          <FormField label="Description (optional)" htmlFor="description">
            <Input id="description" value={description} maxLength={500} onChange={(e) => setDescription(e.target.value)} placeholder="What is this batch for?" />
          </FormField>
          <Button type="submit" icon={Database} loading={generating} disabled={!name.trim() || Number(recordCount) < 500 || Number(recordCount) > 50000}>Send to Synthetix</Button>
        </form>
      </Card>

      <h3>Datasets</h3>
      {error && <Alert variant="danger">{error}</Alert>}
      {datasets === null ? (
        <SkeletonTable rows={3} />
      ) : datasets.length === 0 ? (
        <EmptyState title="No datasets yet" message="Generate your first synthetic dataset above." />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-3)" }}>
          {datasets.map((d) => (
            <RecordCard key={d.id} status={d.status === "needs_regeneration" ? "rejected" : d.status}>
              <div style={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: "var(--sp-3)" }}>
                <div>
                  <div style={{ fontWeight: 700 }}>{d.name}</div>
                  <div className="dataset-meta">
                    <span className="mono">#{d.id}</span> &middot; v{d.version} &middot; {d.recordCount.toLocaleString()} records &middot; generated {d.generatedDate}
                    {d.qualityScore != null && <> &middot; quality score {d.qualityScore}</>}
                  </div>
                  {d.description && <div className="dataset-meta">{d.description}</div>}
                </div>
                <div className="inline-actions">
                  <StatusBadge status={d.status} />
                  {d.status === "evaluating" && (
                    <Button size="sm" onClick={() => handleEvaluate(d.id)} loading={busyId === d.id}>Evaluate</Button>
                  )}
                  {d.status === "needs_regeneration" && (
                    <Button size="sm" variant="secondary" icon={RotateCcw} onClick={() => setRegen({ dataset: d, recordCount: Math.min(50000, Math.max(d.recordCount * 2, 5000)) })}>
                      Regenerate
                    </Button>
                  )}
                  {d.status !== "evaluating" || d.report ? (
                    <Button size="sm" variant="ghost" icon={FileBarChart} onClick={() => openDetail(d)}>Report</Button>
                  ) : (
                    <Button size="sm" variant="ghost" icon={Eye} onClick={() => openDetail(d)}>Details</Button>
                  )}
                </div>
              </div>
            </RecordCard>
          ))}
        </div>
      )}

      {regen && (
        <Modal title="Regenerate dataset" onClose={() => setRegen(null)}>
          <p>Synthetix will replace the records of <strong>{regen.dataset.name}</strong> with a fresh sample (version {regen.dataset.version + 1}) and you can evaluate it again.</p>
          <FormField label="Number of records" htmlFor="regenCount" hint="More records make privacy groups larger, which is the usual reason for a failure.">
            <Input id="regenCount" type="number" min="500" max="50000" step="500" value={regen.recordCount}
              onChange={(e) => setRegen({ ...regen, recordCount: Number(e.target.value) })} />
          </FormField>
          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setRegen(null)}>Cancel</Button>
            <Button icon={RotateCcw} loading={busyId === regen.dataset.id} disabled={regen.recordCount < 500 || regen.recordCount > 50000} onClick={handleRegenerate}>Regenerate</Button>
          </div>
        </Modal>
      )}

      {detail && (
        <Modal wide title={`${detail.name} (v${detail.version})`} onClose={() => setDetail(null)}>
          <div className="dataset-meta" style={{ marginBottom: "var(--sp-3)" }}>
            {detail.recordCount.toLocaleString()} records &middot; learned from{" "}
            {detail.profileSource === "REAL_AGGREGATES" ? "suppressed real age and gender aggregates" : "built-in priors (no real data used)"}
          </div>
          <QualityReport report={detail.report} />
          <div className="inline-actions" style={{ marginTop: "var(--sp-5)" }}>
            <Button size="sm" variant="secondary" icon={Eye} loading={previewing} onClick={loadPreview}>Preview rows</Button>
            <Button size="sm" variant="secondary" icon={Download} onClick={download}>Download CSV</Button>
            {detail.status !== "retired" && (
              <Button size="sm" variant="danger" icon={Archive} onClick={() => setRetireTarget(detail)}>Retire</Button>
            )}
          </div>
          {preview && <div style={{ marginTop: "var(--sp-3)" }}><RecordsPreview rows={preview} /></div>}
        </Modal>
      )}

      {retireTarget && (
        <ConfirmDialog
          title="Retire dataset"
          message={`Retire "${retireTarget.name}"? It leaves the researcher catalogue and every researcher loses access to it immediately. This cannot be undone.`}
          confirmLabel="Retire"
          danger
          loading={busyId === retireTarget.id}
          onConfirm={handleRetire}
          onCancel={() => setRetireTarget(null)}
        />
      )}
    </div>
  );
}
