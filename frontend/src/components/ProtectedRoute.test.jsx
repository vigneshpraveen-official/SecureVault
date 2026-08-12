import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../test/renderWithProviders';
import ProtectedRoute from './ProtectedRoute';

function Vault() {
  return <p>Vault contents</p>;
}
function Login() {
  return <p>Login page</p>;
}

function renderProtected(preloadedState) {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/vault" element={<Vault />} />
      </Route>
    </Routes>,
    { route: '/vault', preloadedState },
  );
}

describe('ProtectedRoute', () => {
  it('should_redirectToLogin_when_theUserIsNotAuthenticated', () => {
    renderProtected({ auth: { isAuthenticated: false } });

    expect(screen.getByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Vault contents')).not.toBeInTheDocument();
  });

  it('should_renderTheProtectedRoute_when_theUserIsAuthenticated', () => {
    renderProtected({ auth: { isAuthenticated: true } });

    expect(screen.getByText('Vault contents')).toBeInTheDocument();
    expect(screen.queryByText('Login page')).not.toBeInTheDocument();
  });
});
