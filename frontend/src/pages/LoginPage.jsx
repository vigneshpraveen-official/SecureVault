import { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { ShieldCheck } from 'lucide-react';
import Card from '../components/Card';
import Input from '../components/Input';
import Button from '../components/Button';
import { loginUser, completeMfaChallenge, clearAuthError } from '../features/auth/authSlice';

function LoginForm({ onSubmit, submitting, error }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    onSubmit({ email, password });
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      <Input
        label="Email"
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        disabled={submitting}
        required
      />
      <Input
        label="Password"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        disabled={submitting}
        required
      />
      {error && <p className="text-sm text-danger-600">{error}</p>}
      <Button type="submit" loading={submitting} className="mt-2 w-full">
        Log in
      </Button>
    </form>
  );
}

function MfaForm({ onSubmit, submitting, error }) {
  const [code, setCode] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    onSubmit(code.trim());
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      <p className="text-sm text-neutral-500">
        Enter the 6-digit code from your authenticator app, or one of your backup codes.
      </p>
      <Input
        label="Authentication code"
        inputMode="text"
        autoComplete="one-time-code"
        value={code}
        onChange={(e) => setCode(e.target.value)}
        disabled={submitting}
        autoFocus
        required
      />
      {error && <p className="text-sm text-danger-600">{error}</p>}
      <Button type="submit" loading={submitting} className="mt-2 w-full">
        Verify
      </Button>
    </form>
  );
}

export default function LoginPage() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { status, error, mfaChallengeToken } = useSelector((state) => state.auth);
  const submitting = status === 'loading';

  const redirectTo = location.state?.from?.pathname ?? '/';

  async function handleLogin(credentials) {
    dispatch(clearAuthError());
    const result = await dispatch(loginUser(credentials));
    if (loginUser.fulfilled.match(result) && !result.payload.mfaRequired) {
      navigate(redirectTo, { replace: true });
    }
  }

  async function handleMfaSubmit(code) {
    dispatch(clearAuthError());
    const result = await dispatch(completeMfaChallenge({ challengeToken: mfaChallengeToken, code }));
    if (completeMfaChallenge.fulfilled.match(result)) {
      toast.success('Welcome back.');
      navigate(redirectTo, { replace: true });
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-50 px-4">
      <Card className="w-full max-w-sm p-6">
        <div className="mb-6 flex items-center gap-2 text-lg font-semibold text-neutral-900">
          <ShieldCheck className="h-5 w-5 text-accent-600" aria-hidden="true" />
          {mfaChallengeToken ? 'Two-factor verification' : 'Log in to SecureVault'}
        </div>
        {mfaChallengeToken ? (
          <MfaForm onSubmit={handleMfaSubmit} submitting={submitting} error={error?.message} />
        ) : (
          <LoginForm onSubmit={handleLogin} submitting={submitting} error={error?.message} />
        )}
        {!mfaChallengeToken && (
          <p className="mt-4 text-center text-sm text-neutral-500">
            Don't have an account?{' '}
            <Link to="/register" className="font-medium text-accent-600 hover:underline">
              Create one
            </Link>
          </p>
        )}
      </Card>
    </div>
  );
}
