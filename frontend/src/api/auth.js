import { apiRequest } from './client';

export const authApi = {
  register: (payload) => apiRequest({ method: 'POST', url: '/api/auth/register', data: payload }),
  login: (payload) => apiRequest({ method: 'POST', url: '/api/auth/login', data: payload }),
  refresh: (refreshToken) =>
    apiRequest({ method: 'POST', url: '/api/auth/refresh', data: { refreshToken } }),
  logout: (refreshToken) =>
    apiRequest({ method: 'POST', url: '/api/auth/logout', data: { refreshToken } }),
  mfaChallenge: (challengeToken, code) =>
    apiRequest({ method: 'POST', url: '/api/auth/mfa/challenge', data: { challengeToken, code } }),
  mfaSetup: () => apiRequest({ method: 'POST', url: '/api/auth/mfa/setup' }),
  mfaVerify: (code) => apiRequest({ method: 'POST', url: '/api/auth/mfa/verify', data: { code } }),
  mfaDisable: (code) => apiRequest({ method: 'POST', url: '/api/auth/mfa/disable', data: { code } }),
};
