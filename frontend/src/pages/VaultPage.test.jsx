import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '../test/renderWithProviders';
import { server } from '../test/server';
import VaultPage from './VaultPage';

const HEALTH_RESPONSE = {
  totalCredentials: 0,
  veryWeakCount: 0,
  weakCount: 0,
  mediumCount: 0,
  strongCount: 0,
  veryStrongCount: 0,
  reusedPasswordCount: 0,
  staleCredentialCount: 0,
  healthScore: 100,
};

function credential(overrides = {}) {
  return {
    id: 1,
    title: 'GitHub',
    username: 'dave',
    websiteUrl: 'https://github.com',
    category: 'DEVELOPMENT',
    strengthScore: 5,
    favorite: false,
    updatedAt: '2026-08-01T00:00:00Z',
    historyCount: 0,
    ...overrides,
  };
}

function pageOf(content) {
  return {
    content,
    currentPage: 0,
    pageSize: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    hasNext: false,
  };
}

function mockHealth() {
  server.use(
    http.get('/api/vault/health', () => HttpResponse.json({ success: true, data: HEALTH_RESPONSE })),
  );
}

describe('VaultPage', () => {
  it('should_renderOneRowPerCredential_when_theListLoads', async () => {
    mockHealth();
    server.use(
      http.get('/api/vault', () =>
        HttpResponse.json({ success: true, data: pageOf([credential(), credential({ id: 2, title: 'GitLab' })]) }),
      ),
    );

    renderWithProviders(<VaultPage />, { route: '/vault' });

    expect(await screen.findByText('GitHub')).toBeInTheDocument();
    expect(screen.getByText('GitLab')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3); // header + 2 credential rows
  });

  it('should_showTheGenuinelyEmptyState_when_theVaultHasNoCredentials', async () => {
    mockHealth();
    server.use(http.get('/api/vault', () => HttpResponse.json({ success: true, data: pageOf([]) })));

    renderWithProviders(<VaultPage />, { route: '/vault' });

    expect(await screen.findByText('You have no credentials')).toBeInTheDocument();
    expect(screen.queryByText('No credentials match')).not.toBeInTheDocument();
  });

  it('should_showADistinctErrorState_notTheEmptyState_when_theListFetchFails', async () => {
    mockHealth();
    server.use(
      http.get('/api/vault', () =>
        HttpResponse.json(
          { success: false, message: 'Internal error', errorCode: 'INTERNAL_ERROR', data: null, errors: null },
          { status: 500 },
        ),
      ),
    );

    renderWithProviders(<VaultPage />, { route: '/vault' });

    expect(await screen.findByText("Couldn't load your vault")).toBeInTheDocument();
    expect(screen.queryByText('You have no credentials')).not.toBeInTheDocument();
  });

  it('should_requestTheTypedTitleFilter_when_searching', async () => {
    const user = userEvent.setup();
    mockHealth();
    const seenTitleParams = [];
    server.use(
      http.get('/api/vault', ({ request }) => {
        const url = new URL(request.url);
        seenTitleParams.push(url.searchParams.get('title'));
        return HttpResponse.json({ success: true, data: pageOf([credential()]) });
      }),
    );

    renderWithProviders(<VaultPage />, { route: '/vault' });
    await screen.findByText('GitHub');

    await user.type(screen.getByLabelText('Search'), 'git');

    await waitFor(() => expect(seenTitleParams).toContain('git'), { timeout: 2000 });
  });

  it('should_fetchTheDecryptedPassword_onlyOnReveal_notOnInitialRender', async () => {
    const user = userEvent.setup();
    mockHealth();
    let revealCalls = 0;
    server.use(
      http.get('/api/vault', () => HttpResponse.json({ success: true, data: pageOf([credential()]) })),
      http.get('/api/vault/1', () => {
        revealCalls += 1;
        return HttpResponse.json({
          success: true,
          data: { ...credential(), password: 'ghSecret1!' },
        });
      }),
    );

    renderWithProviders(<VaultPage />, { route: '/vault' });
    await screen.findByText('GitHub');

    expect(revealCalls).toBe(0);

    await user.click(screen.getByRole('button', { name: /Reveal password/ }));

    await waitFor(() => expect(screen.getByText('ghSecret1!')).toBeInTheDocument());
    expect(revealCalls).toBe(1);
  });
});
