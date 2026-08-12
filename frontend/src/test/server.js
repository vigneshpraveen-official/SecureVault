import { setupServer } from 'msw/node';

// Default handlers are deliberately empty — each test registers exactly the endpoints it needs
// via server.use(...), so a test can never accidentally pass because of another test's leftover
// mock. onUnhandledRequest: 'error' (setup.js) makes a missing handler a loud failure, not a
// silent real network call.
export const server = setupServer();
