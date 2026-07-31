/**
 * Boot: load the active instance straight away when one is stored (the local
 * page acts as a brief splash), otherwise render onboarding. Unlike
 * sovereign-desktop (which relies on a native OS menu item to navigate back
 * to the local page), this shell has no persistent chrome — so it preserves
 * WebView history on the way out (`onboarding.ts`'s `loadInstance` uses
 * `assign`, not `replace`) and lets the platform's native back gesture
 * (Android hardware back / iOS edge-swipe, both wired in main-thread native
 * code — see ios/App/App/AppDelegate.swift and
 * android/app/.../MainActivity.kt) return here. See
 * docs/adrs/0006-history-based-instance-switch-affordance.md.
 */
import { getActiveUrl } from './store';
import { renderOnboarding } from './onboarding';

const MANAGE_PARAM = 'manage';

async function boot(): Promise<void> {
  const root = document.getElementById('app');
  if (!root) return;

  const manage = new URLSearchParams(window.location.search).has(MANAGE_PARAM);
  const activeUrl = await getActiveUrl();

  if (activeUrl !== null && !manage) {
    window.location.assign(activeUrl);
    return;
  }

  await renderOnboarding(root);
}

void boot();
