import { useEffect, useState } from "react";
import { Download } from "lucide-react";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Breadcrumb from "../../components/common/Breadcrumb";
import Button from "../../components/common/Button";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";
import * as datasetService from "../../services/datasetService";

export default function MyRequests() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    datasetService.getDatasetRequests({ researcherName: user.name }).then((data) => {
      setRequests(data); setLoading(false);
    });
  }, [user.name]);

  async function download(row) {
    try { await datasetService.downloadDataset(row.datasetId); showToast("Download started.", "success"); }
    catch (err) { showToast(err.message, "danger"); }
  }

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "datasetName", label: "Dataset", sortable: true },
    { key: "purpose", label: "Purpose" },
    { key: "requestDate", label: "Requested", sortable: true },
    { key: "status", label: "Status", render: (row) => <StatusBadge status={row.status} /> },
    {
      key: "actions", label: "",
      render: (row) => row.status === "approved" && row.datasetId ? (
        <Button size="sm" variant="secondary" icon={Download} onClick={() => download(row)}>Download</Button>
      ) : null,
    },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/researcher" }, { label: "My requests" }]} />
      <div className="page-header"><div><h1>My requests</h1><p>Track the status of your dataset access requests.</p></div></div>
      <DataTable
        columns={columns}
        data={requests}
        loading={loading}
        searchPlaceholder="Search by dataset..."
        searchKeys={["datasetName"]}
        emptyTitle="You haven't requested any datasets yet"
      />
    </div>
  );
}
