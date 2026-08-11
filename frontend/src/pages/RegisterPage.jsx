import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { ShieldCheck } from 'lucide-react';
import Card from '../components/Card';
import Input from '../components/Input';
import Button from '../components/Button';
import StrengthMeter from '../components/StrengthMeter';
import { authApi } from '../api/auth';
import { passwordApi } from '../api/password';
import useDebouncedValue from '../hooks/useDebouncedValue';
import { validateRegisterForm } from '../features/auth/registerValidation';
import { fieldErrorsFrom } from '../utils/apiErrors';

export default function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '' });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [strength, setStrength] = useState(null);

  const debouncedPassword = useDebouncedValue(form.password, 400);

  useEffect(() => {
    if (!debouncedPassword) {
      setStrength(null);
      return;
    }
    let cancelled = false;
    passwordApi
      .strength(debouncedPassword)
      .then((result) => {
        if (!cancelled) setStrength(result);
      })
      .catch(() => {
        if (!cancelled) setStrength(null);
      });
    return () => {
      cancelled = true;
    };
  }, [debouncedPassword]);

  function handleChange(field) {
    return (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    const clientErrors = validateRegisterForm(form);
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors);
      return;
    }

    setSubmitting(true);
    setErrors({});
    try {
      await authApi.register({
        fullName: form.fullName,
        email: form.email,
        password: form.password,
      });
      toast.success('Account created — log in to continue.');
      navigate('/login', { replace: true });
    } catch (error) {
      setErrors(fieldErrorsFrom(error));
      toast.error(error.message ?? 'Registration failed.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-50 px-4">
      <Card className="w-full max-w-sm p-6">
        <div className="mb-6 flex items-center gap-2 text-lg font-semibold text-neutral-900">
          <ShieldCheck className="h-5 w-5 text-accent-600" aria-hidden="true" />
          Create your vault
        </div>
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          <Input
            label="Full name"
            autoComplete="name"
            value={form.fullName}
            onChange={handleChange('fullName')}
            error={errors.fullName}
            disabled={submitting}
          />
          <Input
            label="Email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={handleChange('email')}
            error={errors.email}
            disabled={submitting}
          />
          <div>
            <Input
              label="Password"
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={handleChange('password')}
              error={errors.password}
              disabled={submitting}
            />
            {strength && (
              <div className="mt-2">
                <StrengthMeter {...strength} />
              </div>
            )}
          </div>
          <Input
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={handleChange('confirmPassword')}
            error={errors.confirmPassword}
            disabled={submitting}
          />
          <Button type="submit" loading={submitting} className="mt-2 w-full">
            Create account
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-neutral-500">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-accent-600 hover:underline">
            Log in
          </Link>
        </p>
      </Card>
    </div>
  );
}
