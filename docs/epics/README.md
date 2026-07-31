# Sovereign Mobile — Epics overview

A domain-first map of `sovereign-mobile` work, cross-cutting the
phase-sequenced [ROADMAP.md](../../ROADMAP.md). Each task keeps the stable
epic task ID it was assigned centrally in the `sovereign` monorepo's
[epic 20](https://github.com/sovereignfs/sovereign/blob/main/docs/epics/mobile.md)
— this repo does not mint a competing numbering scheme, since several of
these tasks are already cross-referenced from `sovereign`'s own
[workstream 0002](https://github.com/sovereignfs/sovereign/blob/main/docs/workstreams/0002-native-mobile-app-release.md).

Full concept and architecture: [CONCEPT.md](../../CONCEPT.md). Decisions
behind this shape: [docs/adrs/](../adrs/README.md),
[docs/research/](../research/README.md).

## Epics

| Domain                | File                                                         | Task IDs (epic 20)         | Status         | Summary                                                                                                             |
| --------------------- | ------------------------------------------------------------ | -------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------- |
| [Shell](shell.md)     | Onboarding, instance persistence, WebView, navigation policy | 20.1                       | ⏳ In Progress | The Capacitor scaffold itself — first-launch onboarding, multi-instance switching, WebView loading                  |
| [Bridge](bridge.md)   | Device capability transport                                  | 20.3 (rescoped), 20.5–20.9 | 📋 Planned     | This shell's implementation of the shared `@sovereignfs/bridge` transport, and each native capability behind it     |
| [Release](release.md) | Store release, focused plugin apps                           | 20.4, 20.11, 20.12         | 📋 Planned     | App Store / Play Store release of the whole-instance app, and later, focused per-plugin apps from the same codebase |

## Owned elsewhere, referenced here

Some prerequisite tasks live in the `sovereign` monorepo, not this repo.
They're cited across the epics above because they gate work here, but their
deliverables and review checklists are authoritative there, not duplicated
in this repo's docs:

| Task  | What                                                               | Repo                                                                                                                         |
| ----- | ------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| 20.2  | Instance identity/validation endpoint                              | `sovereign` (runtime)                                                                                                        |
| 20.10 | WKWebView service-worker and offline spike                         | Throwaway build, shared with `sovereign` workstream 0001                                                                     |
| —     | `sdk.device.*` capability contract, `@sovereignfs/bridge` protocol | `sovereign` (`packages/sdk`, `packages/bridge`) — see [ADR 0004](../adrs/0004-shared-device-bridge-contract-with-desktop.md) |

_Status key: ✅ Complete · ⏳ In Progress · 📋 Planned_
