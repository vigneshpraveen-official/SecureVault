import { apiRequest } from './client';

export const vaultApi = {
  list: (params) => apiRequest({ method: 'GET', url: '/api/vault', params }),
  search: (q) => apiRequest({ method: 'GET', url: '/api/vault/search', params: { q } }),
  health: () => apiRequest({ method: 'GET', url: '/api/vault/health' }),
  trash: () => apiRequest({ method: 'GET', url: '/api/vault/trash' }),
  history: (id) => apiRequest({ method: 'GET', url: `/api/vault/${id}/history` }),
  getById: (id) => apiRequest({ method: 'GET', url: `/api/vault/${id}` }),
  create: (payload) => apiRequest({ method: 'POST', url: '/api/vault', data: payload }),
  update: (id, payload) => apiRequest({ method: 'PUT', url: `/api/vault/${id}`, data: payload }),
  restore: (id) => apiRequest({ method: 'PUT', url: `/api/vault/${id}/restore` }),
  remove: (id) => apiRequest({ method: 'DELETE', url: `/api/vault/${id}` }),
  permanentDelete: (id) => apiRequest({ method: 'DELETE', url: `/api/vault/${id}/permanent` }),
  recomputeStrength: () => apiRequest({ method: 'POST', url: '/api/vault/recompute-strength' }),
};
