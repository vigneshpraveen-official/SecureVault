import { apiRequest } from './client';

export const passwordApi = {
  strength: (password) =>
    apiRequest({ method: 'POST', url: '/api/password/strength', data: { password } }),
  generate: (config) => apiRequest({ method: 'POST', url: '/api/password/generate', data: config }),
};
