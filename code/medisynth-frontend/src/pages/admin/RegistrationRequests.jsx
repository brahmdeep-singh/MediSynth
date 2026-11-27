import { useEffect, useState, useCallback } from "react";
import Tabs from "../../components/common/Tabs";
import DataTable from "../../components/tables/DataTable";
import Button from "../../components/common/Button";
import ConfirmDialog from "../../components/common/ConfirmDialog";
import Modal from "../../components/common/Modal";
import Textarea from "../../components/common/Textarea";
import FormField from "../../components/common/FormField";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useToast } from "../../context/ToastContext";
import * as adminService from "../../services/adminService";

const TABS = [
  { value: "PENDING_APPROVAL", label: "Pending" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
];
const BADGE = { PENDING_APPROVAL: "warning", APPROVED: "success", REJECTED: "danger" };
const ROLE_LABEL = { DOCTOR: "Doctor", LAB_TECHNICIAN: "Lab technician", RESEARCHER: "Researcher" };
const LABEL = { PENDING_APPROVAL: "Pending", APPROVED: "Approved", REJECTED: "Rejected" };

// Doctor applications, fetched live from the backend. Approval/rejection is
// decided and recorded by the server (an admin cannot approve their own account,
// and a decided request cannot be decided twice).
export default function RegistrationRequests() {
  const { showToast } = useToast();
  const [tab, setTab] = useState("PENDING_APPROVAL");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [approveTarget, setApproveTarget] = useState(null);
  const [rejectTarget, setRejectTarget] = useState(null);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [details, setDetails] = useState(null);

  const load = useCallback(() => {
    setLoading(true); setError(null);
    adminService.getRegistrationRequests({ status: tab })
      .then((list) => setRows(list.map((r) => ({
        ...r,
        specialization: r.doctorProfile?.specialization
          || (r.applicantDetails ? Object.values(r.applicantDetails).join(" / ") : "-"),
        submitted: new Date(r.submittedAt).toLocaleDateString(),
      }))))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [tab]);
  useEffect(load, [load]);

  async function decide(fn, successMsg) {
    setSaving(true);
    try {
      await fn();
      showToast(successMsg, "success");
      load();
    } catch (e) {
      showToast(e.message, "danger"); // e.g. already decided, or self-approval blocked
      load();
    } finally {
      setSaving(false);
      setApproveTarget(null); setRejectTarget(null); setReason("");
    }
  }

  const columns = [
    { key: "applicantName", label: "Applicant", sortable: true },
    { key: "applicantEmail", label: "Email" },
    { key: "requestedRole", label: "Role", render: (r) => ROLE_LABEL[r.requestedRole] || r.requestedRole },
    { key: "specialization", label: "Details" },
    { key: "submitted", label: "Submitted" },
    { key: "status", label: "Status", render: (r) => <span className={`badge badge-${BADGE[r.status]}`}>{LABEL[r.status]}</span> },
    {
      key: "actions", label: "",
      render: (r) => (
        <div className="table-actions">
          <Button size="sm" variant="secondary" onClick={() => setDetails(r)}>Details</Button>
          {r.status === "PENDING_APPROVAL" && (
            <>
              <Button size="sm" onClick={() => setApproveTarget(r)}>Approve</Button>
              <Button size="sm" variant="danger" onClick={() => setRejectTarget(r)}>Reject</Button>
            </>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/admin" }, { label: "Registration requests" }]} />
      <div className="page-header"><div><h1>Registration requests</h1><p>Review doctor, lab technician and researcher applications. Approved applicants can sign in; rejected applicants cannot.</p></div></div>
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      <DataTable
        columns={columns} data={rows} loading={loading} error={error} onRetry={load}
        searchPlaceholder="Search by name or email..." searchKeys={["applicantName", "applicantEmail", "specialization"]}
        emptyTitle="No requests here"
      />

      {approveTarget && (
        <ConfirmDialog
          title="Approve application"
          message={`Approve ${approveTarget.applicantName} as ${(ROLE_LABEL[approveTarget.requestedRole] || "a user").toLowerCase()}? They will be able to sign in immediately.`}
          confirmLabel="Approve" loading={saving}
          onConfirm={() => decide(() => adminService.approveRegistration(approveTarget.id), "Application approved.")}
          onCancel={() => setApproveTarget(null)}
        />
      )}

      {rejectTarget && (
        <Modal title="Reject application" onClose={() => { if (!saving) { setRejectTarget(null); setReason(""); } }}>
          <p>Reject {rejectTarget.applicantName}? The reason is shown to the applicant.</p>
          <FormField label="Reason (optional)" htmlFor="reason">
            <Textarea id="reason" rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
          </FormField>
          <div className="modal-actions">
            <Button variant="secondary" disabled={saving} onClick={() => { setRejectTarget(null); setReason(""); }}>Cancel</Button>
            <Button variant="danger" loading={saving}
              onClick={() => decide(() => adminService.rejectRegistration(rejectTarget.id, reason.trim()), "Application rejected.")}>
              Reject
            </Button>
          </div>
        </Modal>
      )}

      {details && (
        <Modal title="Application details" onClose={() => setDetails(null)}>
          <p style={{ margin: 0 }}><strong>{details.applicantName}</strong> &middot; {details.applicantEmail} &middot; {details.applicantPhone}</p>
          {details.doctorProfile ? (
            <ul style={{ paddingLeft: "1.2em" }}>
              <li>Specialization: {details.doctorProfile.specialization}</li>
              <li>Qualifications: {details.doctorProfile.qualifications}</li>
              <li>Experience: {details.doctorProfile.experienceYears} years</li>
              <li>License number: {details.doctorProfile.licenseNumber}</li>
            </ul>
          ) : details.applicantDetails ? (
            <ul style={{ paddingLeft: "1.2em" }}>
              {Object.entries(details.applicantDetails).map(([k, v]) => <li key={k}>{k}: {v}</li>)}
            </ul>
          ) : <p>No professional details.</p>}
          {details.rejectionReason && <p>Rejection reason: {details.rejectionReason}</p>}
          {details.reviewedByName && <p>Reviewed by {details.reviewedByName}.</p>}
          <div className="modal-actions"><Button variant="secondary" onClick={() => setDetails(null)}>Close</Button></div>
        </Modal>
      )}
    </div>
  );
}
