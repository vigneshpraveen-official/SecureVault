import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { authApi } from '../../api/auth';
import { tokenStore } from '../../api/tokenStore';

const USER_KEY = 'sv:user';

function loadPersistedUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function persistSession(loginResponse) {
  const { accessToken, refreshToken, userId, fullName, email, role } = loginResponse;
  tokenStore.setTokens(accessToken, refreshToken);
  const user = { userId, fullName, email, role };
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  return user;
}

export const registerUser = createAsyncThunk('auth/register', async (payload, { rejectWithValue }) => {
  try {
    return await authApi.register(payload);
  } catch (error) {
    return rejectWithValue(error);
  }
});

export const loginUser = createAsyncThunk('auth/login', async (payload, { rejectWithValue }) => {
  try {
    const response = await authApi.login(payload);
    if (response.mfaRequired) {
      // Challenge token is held only in Redux state (in memory), never persisted —
      // it must not survive a page reload, and it's not a credential worth localStorage risk.
      return { mfaRequired: true, mfaChallengeToken: response.mfaChallengeToken };
    }
    const user = persistSession(response);
    return { mfaRequired: false, user };
  } catch (error) {
    return rejectWithValue(error);
  }
});

export const completeMfaChallenge = createAsyncThunk(
  'auth/mfaChallenge',
  async ({ challengeToken, code }, { rejectWithValue }) => {
    try {
      const response = await authApi.mfaChallenge(challengeToken, code);
      return persistSession(response);
    } catch (error) {
      return rejectWithValue(error);
    }
  },
);

export const logoutUser = createAsyncThunk('auth/logout', async () => {
  const refreshToken = tokenStore.getRefreshToken();
  try {
    if (refreshToken) await authApi.logout(refreshToken);
  } catch {
    // Logout must succeed locally even if the network call fails — the whole point is
    // the user leaves the authenticated state on their own device regardless of server reachability.
  } finally {
    tokenStore.clear();
    localStorage.removeItem(USER_KEY);
  }
});

const initialState = {
  user: loadPersistedUser(),
  isAuthenticated: Boolean(tokenStore.getAccessToken() && loadPersistedUser()),
  mfaChallengeToken: null,
  status: 'idle',
  error: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionExpired(state) {
      state.user = null;
      state.isAuthenticated = false;
      state.mfaChallengeToken = null;
    },
    clearAuthError(state) {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(registerUser.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(registerUser.fulfilled, (state) => {
        state.status = 'idle';
      })
      .addCase(registerUser.rejected, (state, action) => {
        state.status = 'idle';
        state.error = action.payload;
      })
      .addCase(loginUser.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(loginUser.fulfilled, (state, action) => {
        state.status = 'idle';
        if (action.payload.mfaRequired) {
          state.mfaChallengeToken = action.payload.mfaChallengeToken;
        } else {
          state.user = action.payload.user;
          state.isAuthenticated = true;
        }
      })
      .addCase(loginUser.rejected, (state, action) => {
        state.status = 'idle';
        state.error = action.payload;
      })
      .addCase(completeMfaChallenge.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(completeMfaChallenge.fulfilled, (state, action) => {
        state.status = 'idle';
        state.user = action.payload;
        state.isAuthenticated = true;
        state.mfaChallengeToken = null;
      })
      .addCase(completeMfaChallenge.rejected, (state, action) => {
        state.status = 'idle';
        state.error = action.payload;
      })
      .addCase(logoutUser.fulfilled, (state) => {
        state.user = null;
        state.isAuthenticated = false;
        state.mfaChallengeToken = null;
      });
  },
});

export const { sessionExpired, clearAuthError } = authSlice.actions;
export default authSlice.reducer;
