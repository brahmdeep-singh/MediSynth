import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Download, Eye, FileSearch, Table2 } from "lucide-react";
import Card from "../../components/common/Card";
import Button from "../../components/common/Button";
import Alert from "../../components/common/Alert";
import Modal from "../../components/common/Modal";
import RecordCard from "../../components/common/RecordCard";
import StatusBadge from "../../components/common/Badge";
import Breadcrumb from "../../components/common/Breadcrumb";
import EmptyState from "../../components/common/EmptyState";
import { SkeletonTable } from "../../components/common/Skeleton";
import RecordsPreview from "../../components/dataset/RecordsPreview";
import { useToast } from "../../context/ToastContext";
import * as datasetService from "../../services/datasetService";

// Browse datasets that passed Synthetix evaluation. Rows are visible only after an admin approves your request.
export default function DatasetCatalogue() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [items, setItems] = useState(null);
  const [columns, setColumns] = useState([]);
  const [error, setError] = useState("");
  const [showColumns, setShowColumns] = useState(false);
  const [preview, setPreview] = useState(null); // { item, rows }
  const [busyId, setBusyId] = useState(null);

  useEffect(() => {
    datasetService.getCatalogue().then(setItems).catch((e) => { setError(e.message); setItems([]); });
    datasetService.getDatasetColumns().then(setColumns).catch(() => {});
  }, []);

  async function openPreview(item) {
    setBusyId(item.id);
    try { setPreview({ item, rows: await datasetService.getDatasetPreview(item.id, { size: 20 }) }); }
    catch (err) { showToast(err.message, "danger"); }
    finally { setBusyId(null); }
  }

  async function download(item) {
    setBusyId(item.id);
    try { await datasetService.downloadDataset(item.id); showToast("Download started.", "success"); }
    catch (err) { showToast(err.message, "danger"); }
    finally { setBusyId(null); }
  }

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/researcher" }, { label: "Dataset catalogue" }]} />
      <div className="page-header"><div><h1>Dataset catalogue</h1><p>Synthetic datasets that passed Synthetix quality and privacy checks. Request access to preview and download.</p></div></div>

      <Alert variant="info">
        These records are generated, not copied from patients. They are suitable for method development, teaching and pipeline testing,
        not for drawing conclusions about the real patient population.
      </Alert>

      <div style={{ margin: "var(--sp-3) 0" }}>
        <Button size="sm" variant="ghost" icon={Table2} onClick={() => setShowColumns((v) => !v)}>
          {showColumns ? "Hide fields" : "What fields are in every dataset?"}
        </Button>
      </div>
      {showColumns && columns.length > 0 && (
        <Card className="section">
          <div className="preview-scroll">
            <table className="preview-table">
              <thead><tr><th>Field</th><th>Type</th><th>Role</th><th>Meaning</th></tr></thead>
              <tbody>{columns.map((c) => <tr key={c.name}><td className="mono">{c.name}</td><td>{c.type}</td><td>{c.role}</td><td style={{ whiteSpace: "normal" }}>{c.description}</td></tr>)}</tbody>
            </table>
          </div>
        </Card>
      )}

      {error && <Alert variant="danger">{error}</Alert>}
      {items === null ? (
        <SkeletonTable rows={3} />
      ) : items.length === 0 ? (
        <EmptyState title="No datasets available yet" message="Datasets appear here once an admin has generated one and it has passed evaluation." />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-3)" }}>
          {items.map((d) => (
            <RecordCard key={d.id} status={d.accessStatus || "info"}>
              <div style={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: "var(--sp-3)" }}>
                <div style={{ maxWidth: 560 }}>
                  <div style={{ fontWeight: 700 }}>{d.name}</div>
                  <div className="dataset-meta">
                    {d.recordCount.toLocaleString()} records &middot; v{d.version}
                    {d.qualityScore != null && <> &middot; quality score {d.qualityScore}</>}
                    {d.privacyScore ? <> &middot; privacy {(d.privacyScore * 100).toFixed(0)}% (k={d.k})</> : null}
                  </div>
                  {d.description && <div className="dataset-meta">{d.description}</div>}
                </div>
                <div className="inline-actions">
                  {d.accessStatus && <StatusBadge status={d.accessStatus} />}
                  {d.accessStatus === "approved" ? (
                    <>
                      <Button size="sm" variant="secondary" icon={Eye} loading={busyId === d.id} onClick={() => openPreview(d)}>Preview</Button>
                      <Button size="sm" icon={Download} loading={busyId === d.id} onClick={() => download(d)}>Download CSV</Button>
                    </>
                  ) : d.accessStatus === "pending" ? (
                    <span className="dataset-meta">Awaiting admin review</span>
                  ) : (
                    <Button size="sm" icon={FileSearch} onClick={() => navigate(`/researcher/request-access?dataset=${d.id}`)}>
                      {d.accessStatus === "rejected" ? "Request again" : "Request access"}
                    </Button>
                  )}
                </div>
              </div>
            </RecordCard>
          ))}
        </div>
      )}

      {preview && (
        <Modal wide title={`${preview.item.name}: first rows`} onClose={() => setPreview(null)}>
          <RecordsPreview rows={preview.rows} />
        </Modal>
      )}
    </div>
  );
}
