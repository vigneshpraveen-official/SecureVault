import axios from 'axios';
import { tokenStore } from './tokenStore';

// AUTH_ENDPOINTS never trigger the refresh flow on a 401 — a 401 from login/register
// IS the answer (wrong credentials, locked account), not a signal the access token expired.
// /auth/refresh itself is excluded too, so a failed refresh can't recursively try to refresh.
const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/mfa/challenge'];

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use((config) => {
  const token = tokenStore.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Concurrent requests that 401 while a refresh is already in flight queue here instead
// of each independently calling /refresh — that would rotate the refresh token multiple
// times and fail all but the first (S5.2 rotation revokes the old token on every use).
let isRefreshing = false;
let refreshQueue = [];

function resolveQueue(error, accessToken) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(accessToken);
  });
  refreshQueue = [];
}

let onAuthExpired = () => {};
export function setOnAuthExpired(handler) {
  onAuthExpired = handler;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error;
    if (!response || !config) return Promise.reject(error);

    const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => config.url?.includes(path));
    // The backend's AuthenticationEntryPoint (S1.2/S2.3) writes the same errorCode for
    // "no/expired/tampered token" as for a bad login, because both are a 401 at the filter
    // level with no separate TOKEN_EXPIRED signal on the access-token path (that code exists
    // only for the refresh-token endpoint). So: any 401 on a non-auth endpoint from a request
    // that actually carried a bearer token is treated as "access token expired" and gets
    // exactly one refresh attempt, guarded by config._retry against looping.
    const hadToken = Boolean(config.headers?.Authorization);
    if (response.status !== 401 || isAuthEndpoint || !hadToken || config._retry) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        refreshQueue.push({ resolve, reject });
      })
        .then((accessToken) => {
          config._retry = true;
          config.headers.Authorization = `Bearer ${accessToken}`;
          return apiClient(config);
        })
        .catch((queueError) => Promise.reject(queueError));
    }

    config._retry = true;
    isRefreshing = true;
    try {
      const refreshToken = tokenStore.getRefreshToken();
      if (!refreshToken) throw new Error('No refresh token available');

      const { data } = await axios.post(
        `${import.meta.env.VITE_API_BASE_URL}/api/auth/refresh`,
        { refreshToken },
      );
      const { accessToken, refreshToken: newRefreshToken } = data.data;
      tokenStore.setTokens(accessToken, newRefreshToken);
      resolveQueue(null, accessToken);

      config.headers.Authorization = `Bearer ${accessToken}`;
      return apiClient(config);
    } catch (refreshError) {
      resolveQueue(refreshError, null);
      tokenStore.clear();
      onAuthExpired();
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

// Unwraps the ApiResponse envelope so feature code deals in plain data, never
// response.data.data. On error, normalizes to { message, errorCode, errors } so components
// don't reach into axios error internals.
export async function apiRequest(config) {
  try {
    const { data } = await apiClient(config);
    return data.data;
  } catch (error) {
    if (error.response?.data) {
      const { message, errorCode, errors } = error.response.data;
      throw { message, errorCode, errors, status: error.response.status };
    }
    throw { message: 'Network error — please check your connection.', errorCode: 'NETWORK_ERROR' };
  }
}

export default apiClient;
