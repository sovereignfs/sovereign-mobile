import { defineConfig } from 'vite';

// Vite options for the bundled local onboarding/instance-manager page.
// This is *not* Sovereign's own UI — it's the tiny shell chrome loaded via
// Capacitor's local scheme before `location.assign()` navigates the WebView
// to the user's remote instance. See docs/adrs/0005-server-url-not-bundled-assets.md
// for why the actual instance content is never bundled here.
export default defineConfig({
  server: {
    port: 5180,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});
