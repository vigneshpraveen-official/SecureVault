import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './server';

// MSW intercepts at the network boundary (axios/XHR), not by mocking api/*.js modules — per
// S7.4's explicit instruction, this exercises the real request-building code in api/client.js
// (including the ApiResponse-envelope unwrap and error normalization) rather than bypassing it.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// RTL's auto-cleanup-after-each-test only self-registers when it detects Jest-style implicit
// globals; this project runs Vitest with `globals: false` (vitest.config.js), so without this
// every test in a file renders on top of the previous test's still-mounted DOM — found live,
// the second test in CredentialFormModal.test.jsx saw a password input whose value was the
// concatenation of every prior test's typed text.
afterEach(cleanup);
