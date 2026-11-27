import { useMemo, useState } from "react";
import { ChevronUp, ChevronDown, ChevronLeft, ChevronRight } from "lucide-react";
import SearchBar from "../common/SearchBar";
import { SkeletonTable } from "../common/Skeleton";
import EmptyState from "../common/EmptyState";
import ErrorState from "../common/ErrorState";

const PAGE_SIZE = 6;

/**
 * columns: [{ key, label, sortable, render(row) }]
 * data: array of row objects (each needs a stable `id`)
 */
export default function DataTable({
  columns, data, loading, error, onRetry,
  searchPlaceholder = "Search records...", searchKeys,
  emptyTitle = "No records found", emptyMessage,
}) {
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState({ key: null, dir: "asc" });
  const [page, setPage] = useState(1);

  const filtered = useMemo(() => {
    if (!query) return data;
    const q = query.toLowerCase();
    const keys = searchKeys || columns.map((c) => c.key);
    return data.filter((row) => keys.some((k) => String(row[k] ?? "").toLowerCase().includes(q)));
  }, [data, query, searchKeys, columns]);

  const sorted = useMemo(() => {
    if (!sort.key) return filtered;
    const copy = [...filtered];
    copy.sort((a, b) => {
      const av = a[sort.key], bv = b[sort.key];
      if (av === bv) return 0;
      const res = av > bv ? 1 : -1;
      return sort.dir === "asc" ? res : -res;
    });
    return copy;
  }, [filtered, sort]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const pageRows = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  function toggleSort(key) {
    setSort((s) => (s.key === key ? { key, dir: s.dir === "asc" ? "desc" : "asc" } : { key, dir: "asc" }));
  }

  return (
    <div>
      <div className="table-toolbar">
        <SearchBar value={query} onChange={(v) => { setQuery(v); setPage(1); }} placeholder={searchPlaceholder} />
      </div>

      <div className="table-wrap">
        {loading ? (
          <div style={{ padding: "var(--sp-5)" }}><SkeletonTable /></div>
        ) : error ? (
          <div style={{ padding: "var(--sp-5)" }}><ErrorState message={error} onRetry={onRetry} /></div>
        ) : sorted.length === 0 ? (
          <div style={{ padding: "var(--sp-5)" }}>
            <EmptyState title={emptyTitle} message={emptyMessage} />
          </div>
        ) : (
          <>
            <div className="table-scroll">
              <table className="data-table">
                <thead>
                  <tr>
                    {columns.map((col) => (
                      <th
                        key={col.key}
                        className={col.sortable ? "sortable" : ""}
                        onClick={col.sortable ? () => toggleSort(col.key) : undefined}
                      >
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                          {col.label}
                          {col.sortable && sort.key === col.key && (
                            sort.dir === "asc" ? <ChevronUp size={12} /> : <ChevronDown size={12} />
                          )}
                        </span>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {pageRows.map((row) => (
                    <tr key={row.id}>
                      {columns.map((col) => (
                        <td key={col.key} data-label={col.label} className={col.mono ? "id-cell" : ""}>
                          {col.render ? col.render(row) : row[col.key]}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {totalPages > 1 && (
              <div className="table-pagination">
                <span>Page {page} of {totalPages} &middot; {sorted.length} records</span>
                <div className="pagination-controls">
                  <button className="btn btn-ghost btn-sm btn-icon" disabled={page === 1} onClick={() => setPage((p) => p - 1)} aria-label="Previous page">
                    <ChevronLeft size={16} />
                  </button>
                  <button className="btn btn-ghost btn-sm btn-icon" disabled={page === totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Next page">
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
