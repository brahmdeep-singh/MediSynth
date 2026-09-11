import EmptyState from "../common/EmptyState";

export default function RecentActivity({ items }) {
  if (!items || items.length === 0) {
    return <EmptyState title="No recent activity" message="Activity will show up here as it happens." />;
  }
  return (
    <div>
      {items.map((item) => (
        <div className="activity-item" key={item.id}>
          <span className="activity-dot" />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: "var(--fs-sm)", fontWeight: 600 }}>{item.title}</div>
            <div style={{ fontSize: "var(--fs-sm)", color: "var(--text-secondary)" }}>{item.body}</div>
          </div>
          <span className="activity-time">{item.timestamp}</span>
        </div>
      ))}
    </div>
  );
}
