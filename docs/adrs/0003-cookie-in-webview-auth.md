---
status: Accepted
date: 2026-07-31
deciders: kasunben
---

# ADR 0003 — Cookie-in-WebView auth, not OAuth, for v1

## Status

Accepted. Locked by sovereign workstream 0002's "Decisions locked" table.
Revisit once its blocking dependency below clears.

## Context

The user's Sovereign instance already has its own auth (passkeys, TOTP MFA,
sessions). The shell needs to authenticate the WebView against that
instance without duplicating any of it natively.

## Decision

For v1, auth is **cookie-in-WebView**: the user logs into their instance
inside the shell's WebView exactly as they would in a browser, and the
resulting session cookie is scoped to that WebView the same way it would be
in any browser tab.

## Rejected alternatives

- **OAuth-first.** Blocked on sovereign RFC 0072's per-instance,
  admin-only OAuth client registration — a self-hoster would need to
  register this shell as an OAuth client on their own instance before first
  login, which is not viable for a v1 onboarding flow. See sovereign
  workstream 0001 §5 for the full blocking chain.

## Consequences

- No native credential storage or session logic in this repo for v1 — the
  WebView's own cookie jar is the only session state.
- WKWebView's data store can be evicted under storage pressure (see
  [epics/shell.md](../epics/shell.md) risks) — the user-visible effect is a
  forced re-login and cold cache, not data loss, since the instance remains
  the source of truth.
- Revisit this ADR once RFC 0072 ships; do not build OAuth support ahead of
  that dependency clearing.
