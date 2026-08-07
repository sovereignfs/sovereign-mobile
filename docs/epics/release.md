# Epic: Release

> Getting the whole-instance app onto the App Store and Play Store, and —
> later, subject to its own rationing policy — publishing focused,
> single-plugin apps from the same codebase.

## Status

📋 Planned

## Overview

Task 20.4 is this repo's primary reason to exist as a shipped product: the
universal, whole-instance app, publicly released. Tasks 20.11 and 20.12 are
a **later, separately-gated** extension — one native codebase in this repo
parameterized to also build individual, single-plugin apps, per sovereign
RFC 0082. Both are governed centrally; this repo carries the implementation
and the store-facing artifacts.

## Tasks

#### 📋 20.4 — Store release setup and privacy declarations

**Goal:** Prepare iOS App Store and Android Play Store release infrastructure
for the universal, whole-instance shell.

**Deliverables:**

- iOS bundle identifier, Android application ID, icons, splash screens, app
  display metadata. ✅ Icons and splash generated 2026-08-07 from the
  `sovereign` monorepo's brand mark — see `resources/`,
  `scripts/generate-app-assets.mjs`; bundle ID (`fs.sovereign.mobile`) and
  app display name were already set from the initial scaffold.
- App Store Connect and Play Console listing copy explaining the
  user-provided instance URL model. ✅ Drafted 2026-08-07 — see
  [docs/store-listing.md](../store-listing.md) §1.
- App privacy labels / data safety declarations. ✅ Drafted 2026-08-07 — see
  [docs/store-listing.md](../store-listing.md) §2.
- Signing, provisioning, and CI release documentation. **Partly open** — see
  [docs/store-listing.md](../store-listing.md)'s "Still blocked" section;
  needs an Apple Developer Program account and Google Play Console account
  (human handoff) before production signing/CI can be finished.
- Minimum supported iOS and Android versions selected and documented
  (sovereign RFC 0058 open question — resolve here). 🟡 Recommendation
  drafted 2026-08-07 — see [docs/store-listing.md](../store-listing.md) §3
  (keep the existing iOS 15.0 / Android API 24 floors); needs your
  confirmation, not yet treated as decided.
- Store-review checklist covering network access, permissions, and
  self-hosted instance behavior. ✅ Drafted 2026-08-07 — see
  [docs/store-listing.md](../store-listing.md) §4.
- Internal test track (TestFlight / Play internal testing) verified before
  any public release — see [ADR 0002](../adrs/0002-universal-one-binary-distribution.md).
  Blocked on the same account/signing prerequisites above.

**Dependencies:** Task 20.1.

**Technical notes:**

- Reuse the `sovereign-desktop` release's discovered iOS secret set:
  `APPLE_CERTIFICATE`, `APPLE_CERTIFICATE_PASSWORD`, `APPLE_SIGNING_IDENTITY`,
  `APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID` — the team ID was found
  necessary during that release and is easy to miss.
- Privacy labels / data-safety declarations must reflect reality: this
  shell collects nothing itself and connects only to a user-supplied
  instance. No telemetry by default.
- Store copy must not imply Sovereign hosts user data.

**Review checklist:**

- Store listing does not imply Sovereign hosts user data by default.
- Privacy declarations match this shell's actual data collection behavior.
- Required signing/provisioning secrets are documented without committing
  secrets.
- iOS and Android release builds can be produced locally or in CI.
- No telemetry is introduced by default.

---

#### 📋 20.11 — Focused plugin app build targets

**Goal:** Publish an individual plugin as its own native app from this same
codebase — one shell, parameterized, not a second project.

**Deliverables:**

- A declarative build-target config per app: `appId`, `displayName`,
  `defaultInstanceUrl`, `focusPlugin`, icon set. The whole-instance app is
  the target with **no** `focusPlugin`.
- Shell sends the RFC 0080 User-Agent token extended with
  `focus=<pluginId>`.
- `server.url` loads the remote instance over `https` — never bundled
  assets behind `capacitor://`, per
  [ADR 0005](../adrs/0005-server-url-not-bundled-assets.md).
- Instance onboarding extended to validate that the **target plugin** is
  installed, enabled, and surface-compatible — not merely that the URL is a
  Sovereign instance.
- Instance switcher re-validates the target plugin on switch.
- Sign-out drives the platform's own flow so offline cache/queue clearing
  still fires — a native-only sign-out would leak the previous user's cached
  data on a shared device.
- One published focused target, with the whole-instance app still building.

**Dependencies:** Task 20.10 (gate, owned centrally), Task 20.1, Task 20.2
(owned centrally), sovereign RFC 0082.

**Review checklist:**

- Both the whole-instance app and one focused app build from the same
  codebase.
- The focused app loads only its plugin; out-of-focus navigation redirects
  to the plugin root.
- Onboarding rejects an instance that lacks the target plugin, with a clear
  message.
- Offline cold launch renders cached data (if in scope — see
  [research 0003](../research/0003-webview-offline-behavior.md)).
- Sign-out clears the offline cache and queue.
- No auth, role, or plugin-permission logic is duplicated in native code.

---

#### 📋 20.12 — Plugin app store release process and rationing policy

**Goal:** Make publishing a focused plugin app a repeatable process with a
written policy on when it's justified — the ongoing cost is per-app and
permanent.

**Deliverables:**

- Store metadata, privacy labels, and data-safety declarations per focused
  target, making clear the app connects to a user-provided instance and
  Sovereign hosts nothing by default.
- Signing identity, app identifier, and release-ownership model for
  multiple apps.
- **Written rationing policy** (sovereign RFC 0082 §7): the installable PWA
  is the default answer for any plugin wanting an app-like presence; a
  store-published focused app is reserved for flagship plugins where
  distribution or a native capability justifies the review-cycle and
  maintenance cost.
- No telemetry by default.

**Dependencies:** Task 20.11, sovereign RFC 0082.

**Review checklist:**

- One focused app passes store review on both platforms.
- The rationing policy is written and referenced from this epic.
- Store metadata does not imply Sovereign hosts user data.
- No analytics or crash reporting is enabled by default.

## Related docs

- [ADR 0002](../adrs/0002-universal-one-binary-distribution.md),
  [ADR 0005](../adrs/0005-server-url-not-bundled-assets.md)
- [research 0003](../research/0003-webview-offline-behavior.md)
