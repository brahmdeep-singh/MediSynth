import { notifications as seedNotifications } from "../data/notifications";
import { simulateRequest } from "./api";

let notifications = [...seedNotifications];

export function getNotifications(role) {
  return simulateRequest(notifications.filter((n) => n.forRole === role));
}

export function markAllRead(role) {
  notifications = notifications.map((n) => (n.forRole === role ? { ...n, read: true } : n));
  return simulateRequest(true, { delay: 200 });
}
