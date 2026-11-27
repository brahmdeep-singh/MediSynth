import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import Card from "../../components/common/Card";
import Button from "../../components/common/Button";
import FormField from "../../components/common/FormField";
import Select from "../../components/common/Select";
import Textarea from "../../components/common/Textarea";
import Input from "../../components/common/Input";
import Alert from "../../components/common/Alert";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import * as datasetService from "../../services/datasetService";

const OTHER = "__other";

// Mirrors the Researcher swimlane: request dataset access -> (awaits admin review).
// Choose a catalogue dataset, or describe data that is not in the catalogue.
export default function RequestAccess() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [catalogue, setCatalogue] = useState(null);
  const [choice, setChoice] = useState(params.get("dataset") || "");
  const [purpose, setPurpose] = useState("");
  const [title, setTitle] = useState("");
  const [dataScope, setDataScope] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(null);

  useEffect(() => {
    datasetService.getCatalogue().then(setCatalogue).catch((e) => { setError(e.message); setCatalogue([]); });
  }, []);

  // A dataset with a pending or approved request cannot be requested again.
  const available = (catalogue || []).filter((d) => d.accessStatus !== "pending" && d.accessStatus !== "approved");
  const isOther = choice === OTHER;
  const selected = available.find((d) => String(d.id) === String(choice));
  const canSubmit = purpose.trim() && (isOther ? title.trim() : selected);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const req = await datasetService.requestDatasetAccess({
        datasetId: isOther ? undefined : selected.id,
        datasetName: isOther ? title : selected.name,
        dataScope: isOther ? dataScope : undefined,
        researcherName: user.name, institution: user.institution, purpose,
      });
      setSubmitted(req);
      showToast("Access request submitted.", "success");
    } catch (err) {
      setError(err.message);
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
            <p>Your request for "{submitted.datasetName}" has been sent to the MediSynth admin team for review. Once approved you can preview and download it from the catalogue.</p>
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
          {error && <Alert variant="danger">{error}</Alert>}
          <FormField label="Dataset" required htmlFor="dataset">
            <Select id="dataset" value={choice} onChange={(e) => setChoice(e.target.value)} disabled={catalogue === null}>
              <option value="">{catalogue === null ? "Loading datasets..." : "Choose a dataset..."}</option>
              {available.map((d) => (
                <option key={d.id} value={d.id}>{d.name} ({d.recordCount.toLocaleString()} records)</option>
              ))}
              <option value={OTHER}>Something else (not in the catalogue)</option>
            </Select>
          </FormField>
          {isOther && (
            <>
              <FormField label="Data you need" required htmlFor="title" hint="Describe the synthetic data you want. An admin reviews this by hand; nothing is released automatically.">
                <Input id="title" value={title} maxLength={150} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Synthetic cardiac patient cohort" />
              </FormField>
              <FormField label="Fields / scope (optional)" htmlFor="scope">
                <Input id="scope" value={dataScope} maxLength={500} onChange={(e) => setDataScope(e.target.value)} placeholder="e.g. age, diagnosis, lab results" />
              </FormField>
            </>
          )}
          <FormField label="Purpose of use" required htmlFor="purpose" hint="Briefly describe the research this dataset will support.">
            <Textarea id="purpose" maxLength={1000} value={purpose} onChange={(e) => setPurpose(e.target.value)} placeholder="e.g. Benchmarking a readmission-risk model." />
          </FormField>
          <div className="form-actions">
            <Button type="submit" loading={submitting} disabled={!canSubmit}>Submit request</Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
