import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Lock, Unlock, ShieldCheck } from 'lucide-react';
import Card from '../../components/Card';
import Table from '../../components/Table';
import Input from '../../components/Input';
import Badge from '../../components/Badge';
import Button from '../../components/Button';
import Spinner from '../../components/Spinner';
import Pagination from '../../components/Pagination';
import ConfirmDialog from '../../components/ConfirmDialog';
import useDebouncedValue from '../../hooks/useDebouncedValue';
import { adminApi } from '../../api/admin';

const COLUMNS = [
  { key: 'name', label: 'User' },
  { key: 'role', label: 'Role' },
  { key: 'status', label: 'Status' },
  { key: 'mfa', label: 'MFA' },
  { key: 'created', label: 'Joined' },
  { key: 'actions', label: '' },
];

export default function AdminUsersTable() {
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebouncedValue(search, 300);
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [target, setTarget] = useState(null);
  const [updating, setUpdating] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    adminApi
      .users({ page, size: 20, search: debouncedSearch || undefined })
      .then(setData)
      .catch(() => toast.error('Could not load users.'))
      .finally(() => setLoading(false));
  }, [page, debouncedSearch]);

  useEffect(load, [load]);
  useEffect(() => setPage(0), [debouncedSearch]);

  async function handleToggleLock() {
    if (!target) return;
    setUpdating(true);
    try {
      await adminApi.updateUserStatus(target.id, !target.accountLocked);
      toast.success(target.accountLocked ? 'User unlocked.' : 'User locked.');
      setTarget(null);
      load();
    } catch (error) {
      toast.error(error.message ?? 'Could not update user status.');
    } finally {
      setUpdating(false);
    }
  }

  return (
    <Card className="p-4">
      <div className="mb-3 max-w-xs">
        <Input label="Search users" placeholder="Name or email..." value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>
      {loading && !data ? (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      ) : (
        <>
          <Table columns={COLUMNS}>
            {data?.content.map((u) => (
              <tr key={u.id} className="hover:bg-neutral-50">
                <td className="px-4 py-3">
                  <div className="font-medium text-neutral-900">{u.fullName}</div>
                  <div className="text-xs text-neutral-500">{u.email}</div>
                </td>
                <td className="px-4 py-3">
                  <Badge variant={u.role === 'ADMIN' ? 'accent' : 'neutral'}>{u.role}</Badge>
                </td>
                <td className="px-4 py-3">
                  <Badge variant={u.accountLocked ? 'danger' : 'success'}>
                    {u.accountLocked ? 'Locked' : 'Active'}
                  </Badge>
                </td>
                <td className="px-4 py-3">
                  {u.mfaEnabled ? (
                    <ShieldCheck className="h-4 w-4 text-green-600" aria-label="MFA enabled" />
                  ) : (
                    <span className="text-xs text-neutral-400">Off</span>
                  )}
                </td>
                <td className="px-4 py-3 text-neutral-500">{new Date(u.createdAt).toLocaleDateString()}</td>
                <td className="px-4 py-3 text-right">
                  <Button variant="secondary" size="sm" onClick={() => setTarget(u)}>
                    {u.accountLocked ? (
                      <>
                        <Unlock className="h-3.5 w-3.5" aria-hidden="true" />
                        Unlock
                      </>
                    ) : (
                      <>
                        <Lock className="h-3.5 w-3.5" aria-hidden="true" />
                        Lock
                      </>
                    )}
                  </Button>
                </td>
              </tr>
            ))}
          </Table>
          {data && (
            <Pagination
              page={data.currentPage}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              pageSize={data.pageSize}
              onPageChange={setPage}
            />
          )}
        </>
      )}
      <ConfirmDialog
        open={Boolean(target)}
        onClose={() => setTarget(null)}
        onConfirm={handleToggleLock}
        loading={updating}
        variant={target?.accountLocked ? 'primary' : 'danger'}
        confirmLabel={target?.accountLocked ? 'Unlock' : 'Lock'}
        title={target?.accountLocked ? 'Unlock this user?' : 'Lock this user?'}
        description={
          target?.accountLocked
            ? `${target?.email} will be able to log in again, and their failed-attempt counter resets.`
            : `${target?.email} will be immediately unable to log in.`
        }
      />
    </Card>
  );
}
