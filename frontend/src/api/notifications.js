import { apiRequest } from './client';

export const notificationsApi = {
  list: () => apiRequest({ method: 'GET', url: '/api/notifications' }),
  markRead: (id) => apiRequest({ method: 'PUT', url: `/api/notifications/${id}/read` }),
  markAllRead: () => apiRequest({ method: 'PUT', url: '/api/notifications/read-all' }),
};
