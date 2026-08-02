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

- **Bundled local assets over `capacitor://`.** Service workers require a
  secure-context document. On iOS, the `capacitor://` custom scheme yields
  **no service worker at all** — `navigator.serviceWorker` isn't even
  exposed, confirmed empirically by
  [sovereign research 0008](https://github.com/sovereignfs/sovereign/blob/main/docs/research/0008-wkwebview-android-webview-offline-spike.md).
  This foreclosing-offline-entirely outcome is the load-bearing reason this
  ADR exists. **Correction (2026-08-02):** that same research also found
  this is iOS-specific, not a cross-platform fact as originally stated
  here — Android's default bundled scheme (`androidScheme: "https"`, i.e.
  `https://localhost`) **does** support service workers. This doesn't
  change the decision (see Consequences: the real justification is
  [ADR 0002](0002-universal-one-binary-distribution.md)'s "no baked-in
  instance," which applies regardless of what either platform's bundled
  scheme supports — a self-hosted, runtime-chosen instance genuinely
  cannot be baked into a build), only this stated rationale.

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
