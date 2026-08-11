import { useEffect, useState } from 'react';
import { Eye, EyeOff, Wand2 } from 'lucide-react';
import toast from 'react-hot-toast';
import Modal from '../../components/Modal';
import Input from '../../components/Input';
import Button from '../../components/Button';
import StrengthMeter from '../../components/StrengthMeter';
import GeneratorPanel from '../password/GeneratorPanel';
import { passwordApi } from '../../api/password';
import useDebouncedValue from '../../hooks/useDebouncedValue';
import { CATEGORIES } from './categories';
import { validateCredentialForm } from './credentialValidation';
import { fieldErrorsFrom } from '../../utils/apiErrors';

const EMPTY_FORM = { title: '', username: '', password: '', websiteUrl: '', notes: '', category: 'OTHER' };

export default function CredentialFormModal({ open, onClose, onSubmit, submitting, initialValues }) {
  const isEdit = Boolean(initialValues);
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [generatorOpen, setGeneratorOpen] = useState(false);
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
      .then((result) => !cancelled && setStrength(result))
      .catch(() => !cancelled && setStrength(null));
    return () => {
      cancelled = true;
    };
  }, [debouncedPassword]);

  useEffect(() => {
    if (open) {
      setForm(
        initialValues
          ? {
              title: initialValues.title ?? '',
              username: initialValues.username ?? '',
              password: '',
              websiteUrl: initialValues.websiteUrl ?? '',
              notes: initialValues.notes ?? '',
              category: initialValues.category ?? 'OTHER',
            }
          : EMPTY_FORM,
      );
      setErrors({});
      setShowPassword(false);
      setStrength(null);
    }
  }, [open, initialValues]);

  function handleChange(field) {
    return (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    const clientErrors = validateCredentialForm(form, !isEdit);
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors);
      return;
    }

    // Edit: omit password entirely when left blank, so the backend's null-means-unchanged
    // contract (S1.4) applies — sending "" would try to re-encrypt to an empty secret.
    const payload = isEdit
      ? { ...form, password: form.password || undefined }
      : form;

    try {
      await onSubmit(payload);
      onClose();
    } catch (error) {
      setErrors(fieldErrorsFrom(error));
      toast.error(error.message ?? 'Could not save credential.');
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? 'Edit credential' : 'Add credential'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" form="credential-form" loading={submitting}>
            {isEdit ? 'Save changes' : 'Add credential'}
          </Button>
        </>
      }
    >
      <form id="credential-form" onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <Input label="Title" value={form.title} onChange={handleChange('title')} error={errors.title} required />
        <Input label="Username" value={form.username} onChange={handleChange('username')} error={errors.username} />
        <div>
          <label htmlFor="cred-password" className="mb-1 block text-sm font-medium text-neutral-700">
            Password {isEdit && <span className="font-normal text-neutral-400">(leave blank to keep current)</span>}
          </label>
          <div className="flex gap-2">
            <div className="relative flex-1">
              <input
                id="cred-password"
                type={showPassword ? 'text' : 'password'}
                value={form.password}
                onChange={handleChange('password')}
                className={`w-full rounded-sv border px-3 py-2 pr-10 text-sm focus:outline-none focus:ring-2 focus:ring-accent-500 ${
                  errors.password ? 'border-danger-500' : 'border-neutral-300'
                }`}
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-700"
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            <Button type="button" variant="secondary" onClick={() => setGeneratorOpen(true)}>
              <Wand2 className="h-4 w-4" aria-hidden="true" />
              Generate
            </Button>
          </div>
          {errors.password && <p className="mt-1 text-xs text-danger-600">{errors.password}</p>}
          {strength && (
            <div className="mt-2">
              <StrengthMeter {...strength} />
            </div>
          )}
        </div>
        <Input
          label="Website"
          placeholder="https://example.com"
          value={form.websiteUrl}
          onChange={handleChange('websiteUrl')}
          error={errors.websiteUrl}
        />
        <div className="flex flex-col gap-1">
          <label htmlFor="cred-category" className="text-sm font-medium text-neutral-700">
            Category
          </label>
          <select
            id="cred-category"
            value={form.category}
            onChange={handleChange('category')}
            className="rounded-sv border border-neutral-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent-500"
          >
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="cred-notes" className="text-sm font-medium text-neutral-700">
            Notes
          </label>
          <textarea
            id="cred-notes"
            rows={3}
            value={form.notes}
            onChange={handleChange('notes')}
            className={`rounded-sv border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent-500 ${
              errors.notes ? 'border-danger-500' : 'border-neutral-300'
            }`}
          />
          {errors.notes && <p className="text-xs text-danger-600">{errors.notes}</p>}
        </div>
      </form>
      <GeneratorPanel
        open={generatorOpen}
        onClose={() => setGeneratorOpen(false)}
        onUsePassword={(password) => {
          setForm((prev) => ({ ...prev, password }));
          setShowPassword(true);
        }}
      />
    </Modal>
  );
}
