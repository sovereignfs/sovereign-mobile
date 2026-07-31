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
  is enabled in `ios/App/App/AppDelegate.swift`, the one deliberate native
  touch this task adds.

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

## Consequences

- **This is the shell's entire "switch instance while an instance is
  loaded" UX for v1.** It is a genuine, if implicit, affordance — not
  self-evidently discoverable without a hint. Revisit if this becomes a
  support/usability problem once real users are on it (see
  [ROADMAP.md](../../ROADMAP.md) — nothing currently tracks a follow-up
  task for this, since it's unverified need, not a known gap).
- Requires the one line of native customization to `AppDelegate.swift`
  described above; Android needs no equivalent change.
- Any future feature that also wants to navigate the WebView (e.g. a
  future deep-link handler) must also use `assign`, not `replace`, or it
  will silently break this affordance by clobbering the history entry the
  back gesture depends on.
