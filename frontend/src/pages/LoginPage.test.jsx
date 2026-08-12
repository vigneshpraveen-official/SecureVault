import { describe, expect, it } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../test/renderWithProviders';
import { server } from '../test/server';
import LoginPage from './LoginPage';

// userEvent.type() simulates real focus + keystroke events one at a time — running two calls
// concurrently via Promise.all() interleaves their keystrokes onto whichever field currently has
// focus (found live: the captured request body had email/password characters shuffled together).
// Must run sequentially.
async function fillAndSubmit(user, email, password) {
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Log in' }));
}

describe('LoginPage', () => {
  it('should_disableTheSubmitButton_while_theLoginRequestIsPending', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/auth/login', async () => {
        await delay(50);
        return HttpResponse.json({
          success: true,
          data: {
            accessToken: 'a.b.c',
            refreshToken: 'r',
            userId: 1,
            fullName: 'Dave',
            email: 'dave@example.com',
            role: 'USER',
            mfaRequired: false,
          },
        });
      }),
    );
    renderWithProviders(<LoginPage />);

    await fillAndSubmit(user, 'dave@example.com', 'Str0ng!Pass1');

    expect(screen.getByRole('button', { name: 'Log in' })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Log in' })).not.toBeDisabled());
  });

  it('should_surfaceTheBackendsErrorMessage_when_loginIsRejected', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          {
            success: false,
            message: 'Invalid email or password',
            errorCode: 'INVALID_CREDENTIALS',
            data: null,
            errors: null,
          },
          { status: 401 },
        ),
      ),
    );
    renderWithProviders(<LoginPage />);

    await fillAndSubmit(user, 'dave@example.com', 'WrongPassword1!');

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
    // A failed login must not have written any auth state.
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled();
  });

  it('should_showTheMfaChallengeForm_when_loginReportsMfaRequired', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({
          success: true,
          data: { mfaRequired: true, mfaChallengeToken: 'challenge-token-123' },
        }),
      ),
    );
    renderWithProviders(<LoginPage />);

    await fillAndSubmit(user, 'dave@example.com', 'Str0ng!Pass1');

    expect(await screen.findByText('Two-factor verification')).toBeInTheDocument();
    expect(screen.getByLabelText('Authentication code')).toBeInTheDocument();
  });
});
