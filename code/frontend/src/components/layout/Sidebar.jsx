import { NavLink } from "react-router-dom";
import { PanelLeftClose, PanelLeftOpen, LogOut } from "lucide-react";
import { NAV_BY_ROLE } from "../../routes/navConfig";
import { ROLE_LABELS } from "../../data/users";
import { useAuth } from "../../context/AuthContext";

export default function Sidebar({ collapsed, onToggleCollapse, mobileOpen, onCloseMobile }) {
  const { user, logout } = useAuth();
  const items = NAV_BY_ROLE[user.role] || [];

  return (
    <>
      {mobileOpen && <div className="drawer-backdrop" onClick={onCloseMobile} />}
      <aside className={`sidebar${mobileOpen ? " mobile-open" : ""}`} aria-label="Main navigation">
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark">M</div>
          <div>
            <div className="sidebar-brand-name">MediSynth</div>
            <div className="sidebar-brand-role">{ROLE_LABELS[user.role]}</div>
          </div>
        </div>

        <nav className="sidebar-nav">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `sidebar-link${isActive ? " active" : ""}`}
              onClick={onCloseMobile}
            >
              <item.icon />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <button className="sidebar-link" style={{ margin: "0 var(--sp-2) var(--sp-3)", border: "none", background: "none", cursor: "pointer", width: "calc(100% - var(--sp-4))" }} onClick={logout}>
          <LogOut />
          <span>Log out</span>
        </button>

        <button className="sidebar-collapse-btn" onClick={onToggleCollapse} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}>
          {collapsed ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
        </button>
      </aside>
    </>
  );
}
