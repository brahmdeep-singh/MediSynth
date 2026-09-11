import { useEffect, useState } from "react";
import DataTable from "../../components/tables/DataTable";
import StatusBadge from "../../components/common/Badge";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useAuth } from "../../context/AuthContext";
import * as datasetService from "../../services/datasetService";

export default function MyRequests() {
  const { user } = useAuth();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    datasetService.getDatasetRequests({ researcherName: user.name }).then((data) => {
      setRequests(data); setLoading(false);
    });
  }, [user.name]);

  const columns = [
    { key: "id", label: "ID", mono: true },
    { key: "datasetName", label: "Dataset", sortable: true },
    { key: "purpose", label: "Purpose" },
    { key: "requestDate", label: "Requested", sortable: true },
    { key: "status", label: "Status", render: (row) => <StatusBadge status={row.status} /> },
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
