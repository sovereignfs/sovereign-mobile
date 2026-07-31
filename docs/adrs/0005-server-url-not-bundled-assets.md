---
status: Accepted
date: 2026-07-31
deciders: kasunben
---

# ADR 0005 — WebView loads `server.url`, never bundled `capacitor://` assets

## Status

Accepted. Locked by sovereign workstream 0002's "Decisions locked" table,
contingent on the offline spike in
[epics/shell.md](../epics/shell.md) (task 20.10) measuring the actual
tradeoff before implementation.

## Context

Capacitor can serve WebView content two ways: bundled local assets over the
custom `capacitor://` scheme, or a remote origin over `https` via
`server.url`. The choice determines whether a service worker — and
therefore any offline behavior at all — is possible.

## Decision

Point Capacitor's `server.url` at the user's remote instance over `https`.

## Rejected alternatives

- **Bundled local assets over `capacitor://`.** Service workers require an
  `https` document; the `capacitor://` custom scheme yields **no service
  worker at all**, which would foreclose offline behavior entirely and
  contradicts the load-bearing constraint this ADR exists to protect.

## Consequences

- The shell has no meaningfully useful offline-first bundle of its own — it
  is a thin loader for a remote origin, consistent with
  [ADR 0002](0002-universal-one-binary-distribution.md)'s "no baked-in
  instance" decision.
- Actual offline scope (what works without connectivity, what doesn't) is
  determined empirically by the WKWebView offline spike (task 20.10), not
  assumed. See [epics/shell.md](../epics/shell.md) leg detail.
- Any future bundled-asset approach would need its own ADR revisiting this
  one, since it reopens the offline-support question this ADR closes.
