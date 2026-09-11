import { bills as seedBills } from "../data/bills";
import { simulateRequest } from "./api";

let bills = [...seedBills];

export function getBills({ patientName } = {}) {
  let list = bills;
  if (patientName) list = list.filter((b) => b.patientName === patientName);
  return simulateRequest(list);
}

export function payBill(id, method) {
  // 10% simulated decline rate so the payment-failed / retry path in the
  // Activity Diagram is actually reachable in the demo.
  const success = Math.random() > 0.1;
  bills = bills.map((b) =>
    b.id === id
      ? success
        ? { ...b, status: "paid", paymentMethod: method, paidOn: new Date().toISOString().slice(0, 10) }
        : { ...b, status: "failed", paymentMethod: method }
      : b
  );
  return simulateRequest({ success }, { delay: 700 });
}
