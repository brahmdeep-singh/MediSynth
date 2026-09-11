import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import Card from "../../components/common/Card";
import Button from "../../components/common/Button";
import FormField from "../../components/common/FormField";
import Select from "../../components/common/Select";
import Textarea from "../../components/common/Textarea";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import * as datasetService from "../../services/datasetService";

// Mirrors the Researcher swimlane: request dataset access -> (awaits admin review).
export default function RequestAccess() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [datasets, setDatasets] = useState([]);
  const [datasetId, setDatasetId] = useState("");
  const [purpose, setPurpose] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(null);

  useEffect(() => {
    datasetService.getDatasets().then((all) => setDatasets(all.filter((d) => d.status === "ready")));
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    const dataset = datasets.find((d) => d.id === datasetId);
    try {
      const req = await datasetService.requestDatasetAccess({
        datasetId, datasetName: dataset.name, researcherName: user.name, institution: user.institution, purpose,
      });
      setSubmitted(req);
      showToast("Access request submitted.", "success");
    } finally {
      setSubmitting(false);
    }
  }

  if (submitted) {
    return (
      <div className="page">
        <div className="centered-state-page">
          <div className="state-block">
            <CheckCircle2 size={44} color="var(--success)" />
            <h4>Request submitted</h4>
            <p>Your request for "{submitted.datasetName}" has been sent to the MediSynth admin team for review.</p>
            <div style={{ display: "flex", gap: "var(--sp-3)" }}>
              <Button variant="secondary" onClick={() => navigate("/researcher/my-requests")}>View my requests</Button>
              <Button onClick={() => navigate("/researcher")}>Back to dashboard</Button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page" style={{ maxWidth: 560 }}>
      <Breadcrumb items={[{ label: "Dashboard", to: "/researcher" }, { label: "Request access" }]} />
      <div className="page-header"><h1>Request dataset access</h1></div>

      <Card>
        <form onSubmit={handleSubmit}>
          <FormField label="Dataset" required htmlFor="dataset">
            <Select id="dataset" value={datasetId} onChange={(e) => setDatasetId(e.target.value)}>
              <option value="">Choose a dataset...</option>
              {datasets.map((d) => (
                <option key={d.id} value={d.id}>{d.name} ({d.recordCount.toLocaleString()} records)</option>
              ))}
            </Select>
          </FormField>
          <FormField label="Purpose of use" required htmlFor="purpose" hint="Briefly describe the research this dataset will support.">
            <Textarea id="purpose" value={purpose} onChange={(e) => setPurpose(e.target.value)} placeholder="e.g. Benchmarking a readmission-risk model." />
          </FormField>
          <div className="form-actions">
            <Button type="submit" loading={submitting} disabled={!datasetId || !purpose.trim()}>Submit request</Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
