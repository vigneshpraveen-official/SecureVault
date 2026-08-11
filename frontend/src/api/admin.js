import { apiRequest } from './client';

export const adminApi = {
  stats: () => apiRequest({ method: 'GET', url: '/api/admin/stats' }),
  users: (params) => apiRequest({ method: 'GET', url: '/api/admin/users', params }),
  updateUserStatus: (id, locked) =>
    apiRequest({ method: 'PUT', url: `/api/admin/users/${id}/status`, data: { locked } }),
  auditLogs: (params) => apiRequest({ method: 'GET', url: '/api/admin/audit-logs', params }),
};
