import { useState } from 'react';
import Tabs from '../components/Tabs';
import AdminOverview from '../features/admin/AdminOverview';
import AdminUsersTable from '../features/admin/AdminUsersTable';
import AdminAuditLogViewer from '../features/admin/AdminAuditLogViewer';
import AdminSecurityMonitoring from '../features/admin/AdminSecurityMonitoring';

const TABS = [
  { key: 'overview', label: 'Overview', Component: AdminOverview },
  { key: 'users', label: 'Users', Component: AdminUsersTable },
  { key: 'audit', label: 'Audit logs', Component: AdminAuditLogViewer },
  { key: 'monitoring', label: 'Security monitoring', Component: AdminSecurityMonitoring },
];

export default function AdminPage() {
  const [tab, setTab] = useState('overview');
  const Active = TABS.find((t) => t.key === tab).Component;

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold text-neutral-900">Admin</h1>
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      <Active />
    </div>
  );
}
