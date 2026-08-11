import { apiRequest } from './client';

export const dashboardApi = {
  summary: () => apiRequest({ method: 'GET', url: '/api/dashboard/summary' }),
  passwordHealth: () => apiRequest({ method: 'GET', url: '/api/dashboard/password-health' }),
  recentActivity: () => apiRequest({ method: 'GET', url: '/api/dashboard/recent-activity' }),
  alerts: () => apiRequest({ method: 'GET', url: '/api/dashboard/alerts' }),
};
