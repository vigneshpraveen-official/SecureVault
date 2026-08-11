import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import Card from '../../components/Card';
import Table from '../../components/Table';
import Input from '../../components/Input';
import Badge from '../../components/Badge';
import Spinner from '../../components/Spinner';
import Pagination from '../../components/Pagination';
import { adminApi } from '../../api/admin';

const ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'RESTORE', 'PERMANENT_DELETE', 'ACCESS', 'SHARE', 'REVOKE'];

const COLUMNS = [
  { key: 'time', label: 'Time' },
  { key: 'user', label: 'Performed by' },
  { key: 'action', label: 'Action' },
  { key: 'entity', label: 'Entity' },
  { key: 'details', label: 'Details' },
];

export default function AdminAuditLogViewer() {
  const [filters, setFilters] = useState({ userId: '', action: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    adminApi
      .auditLogs({
        page,
        size: 20,
        userId: filters.userId || undefined,
        action: filters.action || undefined,
        from: filters.from ? new Date(filters.from).toISOString() : undefined,
        to: filters.to ? new Date(filters.to).toISOString() : undefined,
      })
      .then(setData)
      .catch(() => toast.error('Could not load audit logs.'))
      .finally(() => setLoading(false));
  }, [page, filters]);

  useEffect(load, [load]);

  function updateFilter(partial) {
    setFilters((prev) => ({ ...prev, ...partial }));
    setPage(0);
  }

  return (
    <Card className="p-4">
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <Input
          label="User ID"
          type="number"
          className="w-28"
          value={filters.userId}
          onChange={(e) => updateFilter({ userId: e.target.value })}
        />
        <div className="flex flex-col gap-1">
          <label htmlFor="audit-action" className="text-sm font-medium text-neutral-700">
            Action
          </label>
          <select
            id="audit-action"
            value={filters.action}
            onChange={(e) => updateFilter({ action: e.target.value })}
            className="rounded-sv border border-neutral-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent-500"
          >
            <option value="">All actions</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
        </div>
        <Input label="From" type="date" value={filters.from} onChange={(e) => updateFilter({ from: e.target.value })} />
        <Input label="To" type="date" value={filters.to} onChange={(e) => updateFilter({ to: e.target.value })} />
      </div>

      {loading && !data ? (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      ) : !data?.content.length ? (
        <p className="py-8 text-center text-sm text-neutral-500">No audit entries match these filters.</p>
      ) : (
        <>
          <Table columns={COLUMNS}>
            {data.content.map((entry) => {
              const date = new Date(entry.timestamp);
              return (
                <tr key={entry.id} className="hover:bg-neutral-50">
                  <td className="px-4 py-3 text-neutral-600" title={date.toISOString()}>
                    {date.toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-neutral-600">{entry.performedBy}</td>
                  <td className="px-4 py-3">
                    <Badge variant="neutral">{entry.action}</Badge>
                  </td>
                  <td className="px-4 py-3 text-neutral-600">
                    {entry.entityType} #{entry.entityId}
                  </td>
                  <td className="px-4 py-3 max-w-xs truncate text-neutral-500" title={entry.details}>
                    {entry.details}
                  </td>
                </tr>
              );
            })}
          </Table>
          <Pagination
            page={data.currentPage}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            pageSize={data.pageSize}
            onPageChange={setPage}
          />
        </>
      )}
    </Card>
  );
}
