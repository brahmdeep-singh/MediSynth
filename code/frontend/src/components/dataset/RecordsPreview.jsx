// Shows the first rows of a dataset. Values are rendered as text by React (no HTML is ever interpreted).
export default function RecordsPreview({ rows }) {
  if (!rows || rows.length === 0) return <p className="dataset-meta">No rows to show.</p>;
  const columns = Object.keys(rows[0]);
  return (
    <div className="preview-scroll" style={{ maxHeight: 360 }}>
      <table className="preview-table">
        <thead><tr>{columns.map((c) => <th key={c}>{c}</th>)}</tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.syntheticId}>{columns.map((c) => <td key={c}>{String(r[c])}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
