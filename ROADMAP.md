# Sovereign Mobile — Roadmap

Chronological task index and **canonical task status**. One row per task.
Full detail for any task lives in [docs/epics/](docs/epics/README.md); this
file tracks only sequencing and status.

Epic task IDs (e.g. `20.1`) are permanent and shared with the `sovereign`
monorepo's own [epic 20](https://github.com/sovereignfs/sovereign/blob/main/docs/epics/mobile.md) —
cite them in commits, PRs, and cross-references. **Roadmap slot versions**
below (`0.1.1`, `0.2.1`, …) are this repo's own and are volatile: they
reflect current sequencing and shift when priorities change. Keep slots out
of branch names and commit subjects.

This repo does not exist yet as a shipped product — every row below is
📋 Planned. See [docs/repositories.md](https://github.com/sovereignfs/sovereign/blob/main/docs/repositories.md)
in the `sovereign` monorepo, which lists this repo's status as "Not yet
created" as of 2026-07-31.

## Phase 1 — Whole-instance shell (sovereign workstream 0002)

The reason this repository is created. Ships the universal app: onboarding,
instance persistence/switching, WebView loading, store release. No device
bridge beyond whatever thin slice RFC 0083 has landed centrally by the time
this phase starts.

| Slot  | Task                                                         | Status | Epic task                                                                        |
| ----- | ------------------------------------------------------------ | ------ | -------------------------------------------------------------------------------- |
| 0.1.1 | Capacitor shell scaffold                                     | ⏳     | [20.1](docs/epics/shell.md#-201--capacitor-shell-scaffold)                       |
| 0.1.2 | Store release setup and privacy declarations (iOS + Android) | 📋     | [20.4](docs/epics/release.md#-204--store-release-setup-and-privacy-declarations) |

**Prerequisites, owned in the `sovereign` monorepo, gate this phase but are
not this repo's own work:** the WKWebView offline spike (task 20.10), the
instance identity/validation endpoint (task 20.2), and the in-app text-size
control that discharges the pinch-zoom accessibility debt (task 10.2). See
sovereign workstream 0002 for that sequencing; this repo's leg 4/5 can begin
once workstream 0002's leg 1 gate clears and leg 2's response contract is
agreed (not necessarily merged).

## Phase 2 — Bridge transport

This shell's implementation of the shared device capability bridge (sovereign
RFC 0083). Nothing in this phase starts before the protocol itself
(`@sovereignfs/bridge`, `@sovereignfs/sdk/device-client`) exists centrally.

| Slot  | Task                                                                                            | Status | Epic task                                                                           |
| ----- | ----------------------------------------------------------------------------------------------- | ------ | ----------------------------------------------------------------------------------- |
| 0.2.1 | Mobile SDK native environment and bridge adapter (Capacitor transport of `@sovereignfs/bridge`) | ✅     | [20.3](docs/epics/bridge.md#-203--mobile-sdk-native-environment-and-bridge-adapter) |

## Phase 3 — Capability breadth

Each row is an additive registry entry against the same bridge contract, not
a new mechanism. Sequencing here is not strict — these can land in any order
once Phase 2 ships, as product need identifies them.

| Slot  | Task                                   | Status | Epic task                                                                 |
| ----- | -------------------------------------- | ------ | ------------------------------------------------------------------------- |
| 0.3.1 | Native push notifications (APNs/FCM)   | 📋     | [20.5](docs/epics/bridge.md#-205--native-push-notifications-apnsfcm)      |
| 0.3.2 | Native photo picker and camera capture | 📋     | [20.6](docs/epics/bridge.md#-206--native-photo-picker-and-camera-capture) |
| 0.3.3 | Biometric auth capability              | 📋     | [20.7](docs/epics/bridge.md#-207--biometric-auth-capability)              |
| 0.3.4 | Haptics capability                     | ✅     | [20.8](docs/epics/bridge.md#-208--haptics-capability)                     |
| 0.3.5 | Background capability planning         | 📋     | [20.9](docs/epics/bridge.md#-209--background-capability-planning)         |

Further candidates (geolocation, calendar, NFC, and others) are tracked as
open questions, not roadmap rows, in
[docs/research/0002-device-capability-candidates.md](docs/research/0002-device-capability-candidates.md)
until a concrete plugin need graduates one onto this list.

## Phase 4 — Focused plugin apps

Separately gated (sovereign workstream 0001, RFC 0082). Publishing
individual, single-plugin apps from this same codebase.

| Slot  | Task                                                  | Status | Epic task                                                                                   |
| ----- | ----------------------------------------------------- | ------ | ------------------------------------------------------------------------------------------- |
| 0.4.1 | Focused plugin app build targets                      | 📋     | [20.11](docs/epics/release.md#-2011--focused-plugin-app-build-targets)                      |
| 0.4.2 | Plugin app store release process and rationing policy | 📋     | [20.12](docs/epics/release.md#-2012--plugin-app-store-release-process-and-rationing-policy) |

## Definition of done — Phase 1 public release

Restated from sovereign workstream 0002, since it's the concrete bar for
this repo's first shipped version:

- [ ] Published on the App Store and Play Store (public release, not just
      an internal track).
- [ ] First launch prompts for an instance URL, validates it, and loads the
      instance; a stored instance loads directly on relaunch.
- [ ] An invalid or unreachable URL fails with an inline error and does not
      crash.
- [ ] A non-Sovereign URL is rejected as such, not merely as "unreachable".
- [ ] Users can add, remove, and switch between at least two instances.
- [ ] External links open outside the primary WebView.
- [ ] Offline behavior matches the scope decided by the offline spike's
      finding, and whatever is not supported is documented rather than
      silently broken.
- [ ] No Sovereign auth, role, or plugin behavior is duplicated in native
      code.
- [ ] `docs/repositories.md` in the `sovereign` monorepo reflects this
      repo's actual status once created and shipping.

## Changelog

| Version | Date       | Change          |
| ------- | ---------- | --------------- |
| 0.1     | 2026-07-31 | Initial roadmap |
