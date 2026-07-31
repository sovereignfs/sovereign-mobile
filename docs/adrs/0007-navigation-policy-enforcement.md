---
status: Accepted
date: 2026-07-31
deciders: kasunben
---

# ADR 0007 — Navigation policy enforced by a thin native WebView delegate

## Status

Accepted. Implements sovereign RFC 0058's explicit navigation-policy
requirement; the mechanism itself is decided in this repo since Capacitor
has no built-in plugin for it.

## Context

RFC 0058 requires: "The shell loads only user-configured Sovereign instances
in the primary WebView. External links should open in the platform browser
or an approved in-app browser surface instead of silently navigating the
shell away from the configured instance." The active instance's origin is
chosen by the user at runtime (per
[ADR 0002](0002-universal-one-binary-distribution.md)), so this cannot be a
static, build-time allow-list — Capacitor's `server.allowNavigation` config
doesn't fit, since it's fixed at build time and this shell has no fixed
instance to list.

## Decision

A thin native delegate on each platform intercepts WebView navigation and
decides same-origin (allow, stays in the WebView) vs. cross-origin (cancel,
open via `@capacitor/browser`'s `Browser.open()` instead):

- **iOS:** `AppDelegate.swift` sets `bridge?.webView?.navigationDelegate`
  (or subclasses the bridge view controller's delegate methods) to
  implement `webView(_:decidePolicyFor:decisionHandler:)`, comparing the
  navigation's target origin against the shell's currently active instance
  origin.
- **Android:** `MainActivity.kt` overrides the bridge's `WebViewClient`
  (`shouldOverrideUrlLoading`) with the same origin comparison.

The active origin is read from `@capacitor/preferences`' `sovereign.activeUrl`
key (the same storage `store.ts` already writes), so native code has no
separate source of truth to keep in sync — it reads exactly what the TS
shell already persists.

## Rejected alternatives

- **Static `server.allowNavigation` config.** Doesn't support a runtime-
  chosen origin; would require a rebuild per instance, contradicting
  [ADR 0002](0002-universal-one-binary-distribution.md).
- **No enforcement; rely on the loaded instance's own link `target`/`rel`
  behavior.** Not this shell's call to make — a plugin author inside the
  loaded instance could link anywhere, and RFC 0058's requirement is
  explicit that the shell, not the instance, owns this policy.

## Consequences

- Two small, platform-native files carry real logic for the first time in
  this repo — acceptable per
  [AGENTS.md](../../AGENTS.md)'s "native code only for glue a Capacitor
  plugin doesn't already cover" rule, since no plugin covers this.
- Both native delegates must be kept in sync with any future change to how
  or where the active origin is stored — a change to `store.ts`'s
  `KEY_ACTIVE_URL` key name breaks native enforcement silently unless
  both platform files are updated in the same change.
- **Not yet verified on-device** — task 20.1's implementation adds the
  code; the "external links do not silently navigate the primary WebView
  away from the configured instance" review-checklist item in
  [docs/epics/shell.md](../epics/shell.md) still needs a physical-device
  or simulator click-through pass to confirm the delegate actually fires
  before this is called done. See Risks in that file — real-device
  verification is a documented human handoff for this repo generally.
