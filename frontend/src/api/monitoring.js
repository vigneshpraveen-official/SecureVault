import { apiRequest } from './client';

export const monitoringApi = {
  devices: () => apiRequest({ method: 'GET', url: '/api/monitoring/devices' }),
  revokeDevice: (id) => apiRequest({ method: 'DELETE', url: `/api/monitoring/devices/${id}` }),
  loginAttempts: (all = false) =>
    apiRequest({ method: 'GET', url: '/api/monitoring/login-attempts', params: all ? { all: true } : {} }),
  alerts: (all = false) =>
    apiRequest({ method: 'GET', url: '/api/monitoring/alerts', params: all ? { all: true } : {} }),
  riskScore: () => apiRequest({ method: 'GET', url: '/api/monitoring/risk-score' }),
};
