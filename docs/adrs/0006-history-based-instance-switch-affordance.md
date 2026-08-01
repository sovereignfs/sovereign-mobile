---
status: Accepted
date: 2026-07-31
deciders: kasunben
---

# ADR 0006 — History-based instance-switch affordance, not native menu

## Status

Accepted. Decided in this repo directly during task 20.1's implementation —
unlike ADRs 0001–0005, this one has no centrally-locked precedent to restate,
because `sovereign-desktop`'s equivalent mechanism doesn't transfer.

## Context

`sovereign-desktop` gets back to its onboarding/instance-manager page via a
native OS menu item ("Instances → Switch Instance…", ⌘⇧I), which navigates
the WebView back to the local page with `?manage=1`. A mobile shell has no
persistent menu bar — there's no equivalent chrome to put a menu item in, and
the WebView otherwise fills the whole screen once an instance is loaded, per
[ADR 0002](0002-universal-one-binary-distribution.md) and
[ADR 0005](0005-server-url-not-bundled-assets.md).

Task 20.1's review checklist still requires "users can add, remove, and
switch between at least two instances," so this shell needs _some_ way to
get from "an instance is loaded full-screen" back to the instance list,
without inventing a persistent native toolbar (which would be a real,
maintained piece of native UI — more shell-side product surface than
[CONCEPT.md](../../CONCEPT.md)'s "no product features in the shell"
principle wants for a first pass).

## Decision

The local onboarding page is pushed onto WebView history rather than
replaced: `onboarding.ts`'s `loadInstance()` calls
`window.location.assign(url)`, not `.replace(url)`, when navigating to an
instance. Standard platform back navigation then returns to it for free:

- **Android:** Capacitor's default `BridgeActivity` already routes the
  hardware back button through the WebView's history stack before falling
  back to exiting the app — no custom code needed.
- **iOS:** WKWebView's `allowsBackForwardNavigationGestures` (edge-swipe)
  is enabled in `MainViewController.swift`'s `capacitorDidLoad()` (not
  `AppDelegate.swift` — that file is unmodified; the bridge view controller
  subclass is where the live `webView` instance is actually reachable).

The result: swiping back (iOS) or pressing hardware/gesture back (Android)
from the root of a loaded instance returns to this shell's instance list,
exactly where a user switching instances wants to land.

## Rejected alternatives

- **A persistent native toolbar with a "Switch Instance" button.** Real,
  maintained native UI surface for both platforms, contradicting the
  minimal-shell philosophy for a first-pass task. Revisit only if user
  testing shows the back-gesture affordance is undiscoverable.
- **A custom URL scheme deep link** (e.g. `sovereignmobile://switch`)
  triggered from an injected floating button. Requires the same kind of
  always-present injected UI as the toolbar option, plus a native
  `WKUserScript`/`evaluateJavascript` injection pipeline that doesn't exist
  yet — more machinery than the outcome justifies here.

### The local page must actively handle being returned to, not just exist

Getting `location.assign()` and `allowsBackForwardNavigationGestures` right
was not sufficient on its own — confirmed by testing the actual gesture
against a real instance, twice over, for two distinct reasons layered on
top of each other:

1. **WKWebView's back/forward cache (bfcache) restores the local page's DOM
   exactly as it was left, without re-running `main.ts`'s top-level
   `void boot()` call.** Since `boot()` had already run to completion (and
   called `location.assign`) before the page was navigated away from, a
   bfcache-restored view showed only the frozen `<p class="splash">`
   markup forever — inert, no redirect, no instance list, nothing. The
   standard fix is listening for `pageshow` and checking
   `event.persisted`, which is `true` exactly when the page came back from
   bfcache rather than a fresh load.
2. **Even without bfcache, `boot()`'s own logic didn't know a load was
   reached by going _back_.** Nothing sets `?manage=1` on a plain back
   navigation, so a fresh (non-bfcache) reload of the local page would see
   `activeUrl !== null && !manage` and immediately redirect forward again —
   bouncing the user straight back to the instance they just tried to
   leave, defeating the entire affordance. Fixed by checking the
   Navigation Timing API's `performance.getEntriesByType('navigation')[0].type`
   for `'back_forward'` and treating that the same as `?manage=1`: skip the
   redirect, render the instance manager instead.

Both fixes live in `src/main.ts`. Neither is native code — this remains a
TypeScript-only fix, consistent with the "native only for glue a Capacitor
plugin doesn't already cover" rule.

## Consequences

- **This is the shell's entire "switch instance while an instance is
  loaded" UX for v1.** It is a genuine, if implicit, affordance — not
  self-evidently discoverable without a hint. Revisit if this becomes a
  support/usability problem once real users are on it (see
  [ROADMAP.md](../../ROADMAP.md) — nothing currently tracks a follow-up
  task for this, since it's unverified need, not a known gap).
- Requires the one line of native customization to `MainViewController.swift`
  described above; Android needs no equivalent change.
- Any future feature that also wants to navigate the WebView (e.g. a
  future deep-link handler) must also use `assign`, not `replace`, or it
  will silently break this affordance by clobbering the history entry the
  back gesture depends on.
- **Verified working on iOS Simulator (2026-08-01)** against a real
  instance (`sovereign.openfs.io`), after the `pageshow`/`back_forward`
  fix above: swiping back from a loaded instance reliably shows the
  instance manager (list, remove, add-another-instance form) rather than a
  dead splash screen, repeatably across multiple connect/back/reconnect
  cycles.
- **Inconclusive on Android Emulator (2026-08-02) — not confirmed working,
  not confirmed broken.** Tested against the same real instance on a fresh
  arm64-v8a API 34 AVD. Results were inconsistent enough that none of them
  should be trusted as the answer on their own:
  - One attempt clearly reached the app (logcat showed `boot()` re-running
    and calling `Preferences.get('sovereign.activeUrl')`) and then hung
    indefinitely — the plugin call never returned, leaving the splash
    frozen. This coincided with the emulator's WebView sandboxed renderer
    process (`com.google.android.webview:sandboxed_process0`) being
    killed and respawned around the same time, visible in `logcat`, which
    points at emulator/renderer instability rather than a logic bug in
    `main.ts` — but this isn't proven either way.
  - Several later attempts (`adb shell input keyevent KEYCODE_BACK`, both
    single and repeated presses, plus an edge-swipe via
    `adb shell input swipe`) never reached the app at all —
    `GoogleInputMethodService` logged consuming the back-key event first
    every time, and no `Capacitor`-tagged log line appeared afterward.
    Whether that's a genuine platform/IME behavior or an artifact of
    synthetic `adb input` injection (as opposed to a real touchscreen
    edge-swipe) is unresolved.
  - A same-session retest with a demonstrably healthy, freshly-restarted
    app process still produced no observable navigation on either input
    method.
  - **Net effect:** unlike the navigation-policy bug (ADR 0007), which was
    cleanly reproduced, root-caused, fixed, and re-verified on both
    platforms, this affordance's Android behavior was not pinned down.
    Treat "does the Android back button reach the instance manager" as an
    **open question**, not a confirmed pass or a confirmed regression —
    real-device testing (not this environment's headless `adb input`
    injection) is the credible way to resolve it, consistent with the
    Risks section of [docs/epics/shell.md](../epics/shell.md).
