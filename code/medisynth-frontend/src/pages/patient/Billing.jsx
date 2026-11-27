import { useEffect, useState } from "react";
import { CreditCard } from "lucide-react";
import Breadcrumb from "../../components/common/Breadcrumb";
import RecordCard from "../../components/common/RecordCard";
import StatusBadge from "../../components/common/Badge";
import Button from "../../components/common/Button";
import Modal from "../../components/common/Modal";
import Select from "../../components/common/Select";
import FormField from "../../components/common/FormField";
import Alert from "../../components/common/Alert";
import EmptyState from "../../components/common/EmptyState";
import { SkeletonTable } from "../../components/common/Skeleton";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import * as billingService from "../../services/billingService";

// Mirrors the Activity Diagram's billing branch: Generate bill -> Patient makes
// payment -> Payment successful? -> [No] Payment failed -> Retry payment / [Yes] Update payment status.
export default function Billing() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [bills, setBills] = useState(null);
  const [payingBill, setPayingBill] = useState(null);
  const [method, setMethod] = useState("UPI");
  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState("");

  function load() {
    billingService.getBills({ patientName: user.name }).then(setBills);
  }
  useEffect(load, [user.name]);

  async function handlePay() {
    setPaying(true);
    setPayError("");
    const result = await billingService.payBill(payingBill.id, method);
    setPaying(false);
    if (result.success) {
      showToast("Payment successful.", "success");
      setPayingBill(null);
      load();
    } else {
      setPayError("Payment failed. Please retry or use a different payment method.");
      load();
    }
  }

  return (
    <div className="page">
      <Breadcrumb items={[{ label: "Dashboard", to: "/patient" }, { label: "Billing & payments" }]} />
      <div className="page-header">
        <div>
          <h1>Billing & payments</h1>
          <p>Review and pay bills generated after your appointments.</p>
        </div>
      </div>

      {bills === null ? (
        <SkeletonTable rows={3} />
      ) : bills.length === 0 ? (
        <EmptyState icon={CreditCard} title="No bills yet" />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-3)" }}>
          {bills.map((b) => (
            <RecordCard key={b.id} status={b.status}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "var(--sp-3)" }}>
                <div>
                  <div style={{ fontWeight: 700 }}>Rs {b.amount.toLocaleString("en-IN")}</div>
                  <div className="mono" style={{ color: "var(--text-secondary)", fontSize: "var(--fs-xs)" }}>{b.id} &middot; appointment {b.appointmentId}</div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-3)" }}>
                  <StatusBadge status={b.status} />
                  {b.status !== "paid" && (
                    <Button size="sm" onClick={() => { setPayingBill(b); setPayError(""); }}>
                      {b.status === "failed" ? "Retry payment" : "Pay now"}
                    </Button>
                  )}
                </div>
              </div>
            </RecordCard>
          ))}
        </div>
      )}

      {payingBill && (
        <Modal title="Make a payment" onClose={() => !paying && setPayingBill(null)}>
          <p>Amount due: <strong>Rs {payingBill.amount.toLocaleString("en-IN")}</strong></p>
          <FormField label="Payment method" htmlFor="method">
            <Select id="method" value={method} onChange={(e) => setMethod(e.target.value)}>
              <option value="UPI">UPI</option>
              <option value="Card">Debit / Credit Card</option>
              <option value="Net Banking">Net Banking</option>
            </Select>
          </FormField>
          {payError && <Alert variant="danger">{payError}</Alert>}
          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setPayingBill(null)} disabled={paying}>Cancel</Button>
            <Button onClick={handlePay} loading={paying}>Pay Rs {payingBill.amount.toLocaleString("en-IN")}</Button>
          </div>
        </Modal>
      )}
    </div>
  );
}
