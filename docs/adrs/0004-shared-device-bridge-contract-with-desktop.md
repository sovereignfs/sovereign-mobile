---
status: Accepted
date: 2026-07-31
deciders: kasunben
---

# ADR 0004 — Device bridge is a shared, versioned contract with `sovereign-desktop`

## Status

Accepted. Protocol owned and designed in sovereign RFC 0083; this ADR
records this repo's commitment to consuming it rather than defining its own.

## Context

Both this repo and `sovereign-desktop` need to expose native device
capabilities (geolocation, camera, calendar, NFC, haptics, push, and future
capabilities) to plugin authors through the same portable `sdk.device.*`
call surface. If each shell repository designed its own bridge protocol
independently, the two would drift the first time a capability shipped to
only one of them — and plugin authors would be back to branching on shell
internals, the exact thing `sdk.device.*` exists to prevent.

## Decision

This repo implements the **Capacitor transport** of a single, published,
versioned contract owned centrally: `@sovereignfs/bridge` (implementation:
transports, protocol, shell-side helper) plus `@sovereignfs/sdk/device-client`
(contract: capability registry, types, `provideBridge()`), both defined and
published from the `sovereign` monorepo per sovereign RFC 0083.
`sovereign-desktop` implements the Tauri transport of the same contract.

Capability negotiation happens at handshake: the shell announces exactly
which capabilities it supports and at which version. Plugins call
`supports('haptics.impact', 1)`; **no code anywhere compares shell
versions.** Results are a typed discriminated union — `available`, `denied`,
`dismissed`, `unavailable` — never a thrown exception, so a plugin can tell
"the user said no" from "this device doesn't have it" from "this shell
hasn't implemented it yet."

## Rejected alternatives

- **A bridge designed independently in this repo.** Would let this shell
  and `sovereign-desktop` diverge on the first capability either one
  shipped — exactly the failure mode RFC 0083 exists to close off.

## Consequences

- This repo must expose **only** the narrow bridge object to page JS —
  **never** raw `window.Capacitor`. Page content (the platform, and any
  plugin it hosts) reaches native capability exclusively through the
  published bridge surface.
- This shell's advertised `capabilities` list at handshake must reflect
  exactly what this build actually implements — never aspirational, never
  stale.
- v1 ships RFC 0083's deliberately thin slice: `haptics.impact` and
  `notifications.native`, plus a platform-internal `secureStorage` tier.
  Broader capabilities (camera, calendar, NFC, geolocation, biometrics —
  see [docs/research/0002-device-capability-candidates.md](../research/0002-device-capability-candidates.md))
  land later as additive registry entries against the same contract, not as
  new mechanisms.
- New `device:*` manifest permissions and per-user consent grants are owned
  by the platform (`packages/manifest`, `runtime`, `plugins/account`), not
  by this repo — this repo implements the transport, not the permission or
  consent model.
- Protocol changes are made in `sovereign`, not here. A capability this
  shell needs that the contract doesn't yet define is a `sovereign` PR
  first, a `sovereign-mobile` PR second.
