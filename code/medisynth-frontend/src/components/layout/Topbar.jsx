import { useEffect, useState } from "react";
import { Menu, Bell, ChevronDown } from "lucide-react";
import SearchBar from "../common/SearchBar";
import Avatar from "../common/Avatar";
import Dropdown from "../common/Dropdown";
import { useAuth } from "../../context/AuthContext";
import { ROLE_LABELS } from "../../data/users";
import * as notificationService from "../../services/notificationService";

export default function Topbar({ onOpenMobileNav }) {
  const { user, logout } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [query, setQuery] = useState("");

  useEffect(() => {
    notificationService.getNotifications(user.role).then(setNotifications);
  }, [user.role]);

  const unreadCount = notifications.filter((n) => !n.read).length;

  function handleOpenNotifications() {
    notificationService.markAllRead(user.role).then(() => {
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    });
  }

  return (
    <header className="topbar">
      <button className="hamburger-btn" onClick={onOpenMobileNav} aria-label="Open navigation menu">
        <Menu size={22} />
      </button>

      <div className="topbar-search">
        <SearchBar value={query} onChange={setQuery} placeholder="Search MediSynth..." />
      </div>

      <div className="topbar-actions">
        <Dropdown
          trigger={
            <button className="topbar-icon-btn" onClick={handleOpenNotifications} aria-label={`Notifications (${unreadCount} unread)`}>
              <Bell size={19} />
              {unreadCount > 0 && <span className="topbar-badge">{unreadCount}</span>}
            </button>
          }
        >
          <div style={{ padding: "var(--sp-2) var(--sp-3)", fontWeight: 700, fontSize: "var(--fs-sm)" }}>Notifications</div>
          <div className="dropdown-divider" />
          {notifications.length === 0 ? (
            <div style={{ padding: "var(--sp-3)", fontSize: "var(--fs-sm)", color: "var(--text-secondary)" }}>No notifications.</div>
          ) : (
            notifications.slice(0, 5).map((n) => (
              <div key={n.id} style={{ padding: "var(--sp-2) var(--sp-3)" }}>
                <div style={{ fontSize: "var(--fs-sm)", fontWeight: 600 }}>{n.title}</div>
                <div style={{ fontSize: "var(--fs-xs)", color: "var(--text-secondary)" }}>{n.body}</div>
              </div>
            ))
          )}
        </Dropdown>

        <Dropdown
          trigger={
            <div className="topbar-user">
              <Avatar name={user.name} size={30} />
              <div>
                <div className="topbar-user-name">{user.name}</div>
                <div className="topbar-user-role">{ROLE_LABELS[user.role]}</div>
              </div>
              <ChevronDown size={14} color="var(--text-secondary)" />
            </div>
          }
        >
          <button className="dropdown-item" onClick={logout}>Log out</button>
        </Dropdown>
      </div>
    </header>
  );
}
