import { describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../test/server';
import CredentialFormModal from './CredentialFormModal';

describe('CredentialFormModal', () => {
  it('should_submitExactlyTheFormFields_when_creatingANewCredential', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    server.use(
      http.post('/api/password/strength', () =>
        HttpResponse.json({
          success: true,
          data: { score: 4, strength: 'Strong', entropyBits: 55, feedback: [] },
        }),
      ),
    );

    render(
      <CredentialFormModal open onClose={onClose} onSubmit={onSubmit} submitting={false} initialValues={null} />,
    );

    await user.type(screen.getByLabelText('Title'), 'GitHub');
    await user.type(screen.getByLabelText('Username'), 'dave');
    await user.type(screen.getByLabelText(/^Password/), 'Str0ng!Passw0rd');
    await user.type(screen.getByLabelText('Website'), 'https://github.com');
    await user.click(screen.getByRole('button', { name: 'Add credential' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith({
      title: 'GitHub',
      username: 'dave',
      password: 'Str0ng!Passw0rd',
      websiteUrl: 'https://github.com',
      notes: '',
      category: 'OTHER',
    });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should_rejectSubmission_when_titleIsBlank_withoutCallingOnSubmit', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(
      <CredentialFormModal open onClose={vi.fn()} onSubmit={onSubmit} submitting={false} initialValues={null} />,
    );

    await user.type(screen.getByLabelText(/^Password/), 'Str0ng!Passw0rd');
    await user.click(screen.getByRole('button', { name: 'Add credential' }));

    expect(await screen.findByText('Title is required.')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('should_renderTheStrengthMeterFromTheMockedApi_when_typingAPassword', async () => {
    const user = userEvent.setup();
    server.use(
      http.post('/api/password/strength', () =>
        HttpResponse.json({
          success: true,
          data: {
            score: 2,
            strength: 'Weak',
            entropyBits: 18.4,
            feedback: ['Increase length to 12+ characters'],
          },
        }),
      ),
    );

    render(
      <CredentialFormModal open onClose={vi.fn()} onSubmit={vi.fn()} submitting={false} initialValues={null} />,
    );

    await user.type(screen.getByLabelText(/^Password/), 'weakpw1');

    expect(await screen.findByText('Weak', {}, { timeout: 2000 })).toBeInTheDocument();
    expect(screen.getByText('Increase length to 12+ characters')).toBeInTheDocument();
  });

  it('should_omitThePasswordField_when_editingAndLeavingItBlank', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const initialValues = {
      id: 7,
      title: 'GitHub',
      username: 'dave',
      websiteUrl: '',
      notes: '',
      category: 'DEVELOPMENT',
    };

    render(
      <CredentialFormModal open onClose={vi.fn()} onSubmit={onSubmit} submitting={false} initialValues={initialValues} />,
    );

    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.password).toBeUndefined();
    expect(payload.title).toBe('GitHub');
  });
});
