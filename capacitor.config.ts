import type { CapacitorConfig } from '@capacitor/cli';

// appId follows sovereign-desktop's tauri.conf.json "identifier" convention
// (fs.sovereign.desktop) — reverse-DNS of the sovereignfs.io domain.
const config: CapacitorConfig = {
  appId: 'fs.sovereign.mobile',
  appName: 'Sovereign',
  webDir: 'dist',
  // No `server.url` here — see docs/adrs/0005-server-url-not-bundled-assets.md.
  // The bundled local page (this repo's own onboarding chrome) loads via
  // Capacitor's default local scheme; `location.assign()` then navigates the
  // *same* WebView to the user's remote https instance at runtime, exactly
  // like sovereign-desktop's `tauri://` splash → `location.replace()` pattern.
  // The destination is a normal https navigation, so its service worker
  // registers normally — bundling the destination itself would not.
  plugins: {
    // Routes fetch()/XMLHttpRequest through native code on iOS/Android so
    // instance health checks aren't blocked by CORS — the mobile equivalent
    // of sovereign-desktop's @tauri-apps/plugin-http. Has no effect in a
    // plain browser (`pnpm dev:web`); see src/onboarding.ts.
    CapacitorHttp: {
      enabled: true,
    },
  },
};

export default config;
