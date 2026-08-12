import { configureStore } from '@reduxjs/toolkit';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { render } from '@testing-library/react';
import authReducer from '../features/auth/authSlice';
import vaultReducer from '../features/vault/vaultSlice';

// A fresh store per call, never the app's real singleton (app/store.js) — that one reads
// localStorage and wires setOnAuthExpired as import-time side effects, neither of which a
// component test should depend on. preloadedState lets each test start from the exact auth/vault
// state its scenario needs instead of always going through a real login flow first.
export function renderWithProviders(ui, { preloadedState = {}, route = '/', ...renderOptions } = {}) {
  const store = configureStore({
    reducer: { auth: authReducer, vault: vaultReducer },
    preloadedState,
  });

  function Wrapper({ children }) {
    return (
      <Provider store={store}>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </Provider>
    );
  }

  return { store, ...render(ui, { wrapper: Wrapper, ...renderOptions }) };
}
