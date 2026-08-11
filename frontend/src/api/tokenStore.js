// Thin wrapper around localStorage for the two auth tokens. Kept separate from
// Redux state so api/client.js (below the store, imported first) can read/write
// tokens without importing the store and creating a circular dependency.
const ACCESS_KEY = 'sv:accessToken';
const REFRESH_KEY = 'sv:refreshToken';

export const tokenStore = {
  getAccessToken: () => localStorage.getItem(ACCESS_KEY),
  getRefreshToken: () => localStorage.getItem(REFRESH_KEY),
  setTokens(accessToken, refreshToken) {
    localStorage.setItem(ACCESS_KEY, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};
