import { apiRequest } from './client';

export const sharingApi = {
  create: (payload) => apiRequest({ method: 'POST', url: '/api/share', data: payload }),
  received: () => apiRequest({ method: 'GET', url: '/api/share/received' }),
  sent: () => apiRequest({ method: 'GET', url: '/api/share/sent' }),
  updatePermission: (shareId, permission) =>
    apiRequest({ method: 'PUT', url: `/api/share/${shareId}`, data: { permission } }),
  revoke: (shareId) => apiRequest({ method: 'DELETE', url: `/api/share/${shareId}` }),
};
