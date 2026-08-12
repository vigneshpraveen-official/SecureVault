import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Separate from vite.config.js deliberately: the app's real build config has no reason to know
// about the test environment (jsdom, setup files), and vitest's own `test` block would otherwise
// sit unused inside the production Vite config.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    globals: false,
    css: false,
  },
});
