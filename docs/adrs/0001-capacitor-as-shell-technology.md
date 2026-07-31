---
status: Accepted
date: 2026-07-31
deciders: kasunben
---

# ADR 0001 — Capacitor as the shell technology

## Status

Accepted. Locked by sovereign RFC 0058 and reaffirmed in sovereign
workstream 0002's "Decisions locked" table before this repository was
created; recorded here as this repo's own decision of record.

## Context

Sovereign needs one native mobile client that loads a user's self-hosted
instance without duplicating the platform's auth, plugin, or shell logic. It
must ship a single binary that works against any instance URL, share as much
logic and mental model as possible with the already-shipped
`sovereign-desktop` (Tauri) shell, and expose native device capabilities to
plugin authors through a portable SDK surface.

## Decision

Build the shell with **Capacitor** (TypeScript-first, WKWebView on iOS,
Android System WebView on Android).

## Rejected alternatives

- **Hotwire Native.** Requires separate native bridge code per platform,
  working against the "one TypeScript-first shell logic" goal this repo
  shares with `sovereign-desktop`.
- **React Native.** Rebuilds the platform UI as a second client rendered
  natively — the opposite of the "thin WebView wrapper, source of truth
  stays the instance" model this repo exists to implement. (Note:
  `sovereign-edge`, a separate and unrelated repository, does use React
  Native — but it is building a real native app with its own UI by design,
  not a WebView shell. Its rationale does not transfer here.)

## Consequences

- Shell logic stays TypeScript, matching `sovereign-desktop` and the rest of
  the Sovereign codebase; native Swift/Kotlin is used only where a Capacitor
  plugin genuinely requires it.
- Device capability access goes through Capacitor plugins, fronted by the
  shared `sdk.device.*` contract (see
  [ADR 0004](0004-shared-device-bridge-contract-with-desktop.md)).
- iOS and Android project files are committed to this repo (see
  [epics/shell.md](../epics/shell.md), task 20.1).
