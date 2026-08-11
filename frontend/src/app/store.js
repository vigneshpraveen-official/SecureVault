import { configureStore } from '@reduxjs/toolkit';
import authReducer, { sessionExpired } from '../features/auth/authSlice';
import vaultReducer from '../features/vault/vaultSlice';
import { setOnAuthExpired } from '../api/client';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    vault: vaultReducer,
  },
});

// Wired here (not inside client.js) so the axios layer stays store-agnostic — it only
// knows "auth expired," not that Redux exists.
setOnAuthExpired(() => store.dispatch(sessionExpired()));
