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
 *
 * Landing back on this page via that gesture is *not* the same as a fresh
 * launch, and needs different handling — confirmed empirically (a real
 * back-gesture left the app stuck on a dead splash screen, twice over, for
 * two distinct reasons):
 *
 * 1. A `history.back()`-style navigation without page-cache eviction
 *    restores the DOM exactly as it was when the page was left — WebKit's
 *    back/forward cache (bfcache) does **not** re-run this module's
 *    top-level `void boot()` call. `pageshow`'s `event.persisted` is the
 *    standard way to detect that restoration and re-render explicitly.
 * 2. Even when the module *does* re-run (bfcache eviction forces a fresh
 *    reload instead), `boot()`'s own redirect logic doesn't know the load
 *    was reached by going *back* — nothing sets `?manage=1` on a plain
 *    back-navigation — so it would immediately redirect forward again,
 *    bouncing the user straight back to the instance they just tried to
 *    leave. The Navigation Timing API's `type` distinguishes this
 *    ('back_forward') from a genuine cold launch ('navigate'/'reload').
 */
import { getActiveUrl } from './store';
import { renderOnboarding } from './onboarding';

const MANAGE_PARAM = 'manage';

function isBackForwardNavigation(): boolean {
  const [entry] = performance.getEntriesByType('navigation') as PerformanceNavigationTiming[];
  return entry?.type === 'back_forward';
}

async function boot(): Promise<void> {
  const root = document.getElementById('app');
  if (!root) return;

  const manage = new URLSearchParams(window.location.search).has(MANAGE_PARAM);
  const activeUrl = await getActiveUrl();

  if (activeUrl !== null && !manage && !isBackForwardNavigation()) {
    window.location.assign(activeUrl);
    return;
  }

  await renderOnboarding(root);
}

void boot();

window.addEventListener('pageshow', (event) => {
  if (!event.persisted) return;
  const root = document.getElementById('app');
  if (root) void renderOnboarding(root);
});
