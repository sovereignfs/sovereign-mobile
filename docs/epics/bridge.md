# Epic: Bridge

> This shell's implementation of the shared device capability bridge — the
> mechanism that lets a plugin running inside the WebView call
> `sdk.device.*` and reach real device hardware, without ever importing
> Capacitor or branching on shell internals.

## Status

⏳ In Progress — 20.3 done (adapter + haptics.impact + notifications.native,
per [ADR 0004](../adrs/0004-shared-device-bridge-contract-with-desktop.md)'s
"v1 ships the thin slice" framing, not split across separate tasks as this
doc originally implied); 20.5–20.9 still 📋 Planned

## Overview

The bridge **protocol** — capability registry, negotiation, typed results —
is designed once, centrally, in the `sovereign` monorepo per sovereign RFC
0083 (`@sovereignfs/bridge` + `@sovereignfs/sdk/device-client`), and shared
with `sovereign-desktop`. This repo's job is narrower: implement the
Capacitor **transport** of that contract, and — capability by capability —
the native side of each device call. See
[ADR 0004](../adrs/0004-shared-device-bridge-contract-with-desktop.md) for
why the protocol is not designed here.

Two rules carry through every task in this epic, restated from RFC 0083 and
ADR 0004 because violating either breaks the contract for `sovereign-desktop`
too, not just this repo:

1. The shell must expose **only** the narrow bridge object to page JS —
   never raw `window.Capacitor`.
2. The advertised `capabilities` list at handshake must reflect exactly what
   this build actually implements — never aspirational.

## Tasks

#### ✅ 20.3 — Mobile SDK native environment and bridge adapter

> **Rescoped by sovereign RFC 0083.** The environment-detection half is
> covered centrally by sovereign task 3.32 (RFC 0080). What remains here is
> **the Capacitor transport of `@sovereignfs/bridge`** — tracked as leg 4 of
> sovereign workstream 0003.

**Goal:** Implement this shell's side of the shared device bridge transport
so `sdk.device.*` calls made by plugins running in this WebView reach real
Capacitor plugin calls.

**Deliverables:**

- Capacitor transport implementation consuming `@sovereignfs/bridge`.
- Handshake wiring: this shell announces its actual supported capability set
  and versions on load.
- Only the narrow bridge object injected into page JS — never
  `window.Capacitor` directly.
- Tests covering handshake, capability negotiation, and the
  available/denied/dismissed/unavailable result shape.

**Dependencies:** Task 20.1; sovereign RFC 0083 (protocol, owned centrally).

**Review checklist:**

- Plugin code calls `sdk.device.*` without importing Capacitor.
- This shell's handshake capability list matches what the build actually
  implements.
- `window.Capacitor` is not reachable from page JS.
- Unsupported capabilities return the documented typed result, not a thrown
  exception.

**Outcome (2026-08, both platforms verified empirically, not just compiled):**

- **iOS and Android needed genuinely different designs** because Capacitor's
  own bridge isolation posture differs by platform — this wasn't assumed
  going in, it came from reading each platform's actual `@capacitor/ios` /
  `@capacitor/android` 8.4.2 source before writing any native code:
  - **iOS**: `window.Capacitor` is a WebView-level `WKUserScript` with no
    origin scoping (`forMainFrameOnly: true`, no origin filter) — it runs on
    _every_ navigation, including the loaded remote instance, by default.
    Genuine isolation required actively fighting the framework:
    `MainViewController.swift` swaps the `WKUserContentController`'s
    script/handler set at the local↔remote navigation boundary, replacing
    Capacitor's own scripts and `"bridge"` `WKScriptMessageHandler` with the
    narrow `__SOVEREIGN_BRIDGE__` script and a `sovereignBridge` handler
    (`Bridge.swift`), restoring Capacitor's own on the way back.
  - **Android**: the opposite problem. `Bridge.java`'s `loadWebView()`
    already registers both `window.Capacitor` (`WebViewCompat.addDocumentStartJavaScript`)
    and the native `androidBridge` channel (`WebViewCompat.addWebMessageListener`)
    scoped to `bridge.getAllowedOriginRules()` — which, since this app never
    sets `server.url`, contains only the local origin. So `window.Capacitor`
    never reached the remote instance to begin with; nothing to remove.
    `NavigationPolicyWebViewClient.java` only needed to _add_ its own
    `sovereignBridge` registration (`BridgeMessageListener.java`), using the
    same origin-scoped APIs, scoped to the runtime-chosen active instance
    origin instead of the static local one.
- **Empirically verified end-to-end on both platforms** (iOS Simulator,
  Android Emulator via this repo's bundled `.toolchain/`): confirmed
  `window.Capacitor === undefined` on the loaded remote instance, confirmed
  `window.__SOVEREIGN_BRIDGE__.invoke('haptics.impact', ...)` and
  `invoke('notifications.native', ...)` both round-trip to `{status:'ok'}` —
  including the real OS permission prompt firing (accepted, "Allow") —
  and confirmed navigating back to local content restores normal Capacitor
  function (`@capacitor/preferences`-backed instance list still renders).
  **Only the `ok` path was exercised this way.** `denied` (permission
  refused), `dismissed`, and `unavailable` (unknown capability name) are
  each a single, low-risk code path — the same `default: unavailable(...)`
  shape already shipped on `sovereign-desktop`'s Tauri transport — but
  weren't separately clicked through on either platform; a reviewer who
  wants that specific coverage before merge should ask for it explicitly
  rather than assume it's implied by "empirically verified."
- **No automated native tests were added** — this repo has no existing
  XCTest/Espresso harness to extend, and the capability logic lives entirely
  in native code unreachable from the Vitest/jsdom-based TS test suite.
  Verification is the empirical simulator/emulator round-trip above, the
  same precedent `sovereign-desktop`'s Tauri transport (leg 3) set. A real
  gap against this task's "Tests covering..." deliverable as originally
  written, flagged rather than silently claimed as satisfied.
- `getPermission()`/`requestPermission()` (`packages/sdk/src/device-client.ts`
  in the monorepo) already report `'granted'` unconditionally on any
  native-bridge transport, per the fix that shipped with leg 3 — no further
  monorepo change was needed for this leg.

---

#### 📋 20.5 — Native push notifications (APNs/FCM)

**Goal:** Add native push notification support so users receive alerts when
the app is not open.

**Deliverables:**

- Capacitor push notifications integration for APNs and FCM.
- Native push registration/revocation routed through `sdk.device.*` /
  notification SDK.
- Permission strings, privacy declarations, operator configuration docs.
- Tests for token registration, revocation, and permission/error states.

**Dependencies:** Task 20.3; sovereign RFC 0015 (Notification Center),
RFC 0016 (Web Push) — both owned centrally.

**Review checklist:**

- User can opt in to push on iOS and Android.
- Revoking permission or signing out removes or invalidates the device
  token.
- Push payloads do not expose sensitive content beyond documented behavior.
- Missing APNs/FCM configuration degrades to a documented no-op.

---

#### 📋 20.6 — Native photo picker and camera capture

**Goal:** Expose native photo selection and camera capture through
`sdk.device.*` without plugins importing Capacitor directly.

**Deliverables:**

- Capacitor camera/photo picker integration.
- SDK device method for capture/select flow.
- Browser fallback using existing Web APIs where available.
- Permission strings and privacy declarations for iOS and Android.
- Example/test plugin flow proving portability across browser and this
  shell.

**Dependencies:** Task 20.3.

**Review checklist:**

- Plugin can request a photo through `sdk.device.*` in this shell.
- iOS and Android permission prompts use accurate copy.
- Browser fallback works or returns a documented unsupported state.
- Returned file/blob metadata is normalized across environments.
- Denied permissions are handled without crashing the plugin.

---

#### 📋 20.7 — Biometric auth capability

**Goal:** Add Face ID / fingerprint capability through `sdk.device.*` for
high-trust local confirmation flows — **without** replacing Sovereign
server-side auth. See [ADR 0003](../adrs/0003-cookie-in-webview-auth.md):
this capability never grants a session by itself.

**Deliverables:**

- Capacitor biometric auth integration.
- SDK device method for local biometric confirmation.
- Clear distinction, in both code and docs, between local device
  confirmation and platform authentication/session freshness.
- Permission/privacy documentation for iOS and Android.
- Tests or simulator verification for success, failure, unavailable, and
  denied states.

**Dependencies:** Task 20.3; coordinate with auth/session freshness rules
(owned centrally) before using this for sensitive flows.

**Review checklist:**

- Biometric prompt can confirm a local action in this shell.
- Capability never grants server-side auth by itself.
- Devices without biometrics return a documented unsupported state.
- Failed or cancelled biometric prompts are handled predictably.

---

#### ✅ 20.8 — Haptics capability

> **Closed by 20.3.** This task predates RFC 0083 and sketched a richer
> four-style feedback surface (success/warning/error/light). The actual
> shipped contract is `haptics.impact('light' | 'medium' | 'heavy')`,
> matching `sovereign-desktop`'s Tauri transport and the monorepo SDK
> exactly — see [ADR 0004](../adrs/0004-shared-device-bridge-contract-with-desktop.md),
> which folds haptics into v1's thin slice alongside `notifications.native`
> rather than a separate later task. Nothing separate remains here.

**Goal:** Expose lightweight native haptics through `sdk.device.*` for
mobile interaction feedback.

**Deliverables:**

- Capacitor haptics integration.
- SDK device method or environment-routed implementation.
- No-op browser fallback.
- Usage guidance keeping haptics optional and non-essential.

**Dependencies:** Task 20.3.

**Review checklist:**

- This shell can trigger success/warning/error/light feedback.
- Browser and PWA environments no-op without throwing.
- Plugins remain fully usable when haptics are unavailable.
- Reduced-motion/accessibility preferences are respected where exposed.

---

#### 📋 20.9 — Background capability planning

**Goal:** Define whether and how native background location or background
work belongs in Sovereign, before any high-risk background permission is
added to this shell.

**Deliverables:**

- Follow-up RFC or design note for background location and background work
  (owned centrally, coordinated from here).
- Store-review and privacy analysis for iOS and Android.
- Capability gating model for plugins that request background behavior.
- Operator/user consent and revocation model.
- Decision on whether background work is handled by this shell's APIs,
  platform jobs, plugin jobs, or a combination.

**Dependencies:** Task 20.3; sovereign RFC 0046 (Plugin background jobs and
schedules).

**Review checklist:**

- No background permission is added before the design is accepted.
- The design identifies data collection, retention, and revocation
  behavior.
- Store-review risk is documented before implementation.

## Beyond the current task list

[Research 0002](../research/0002-device-capability-candidates.md) tracks
capability candidates (geolocation, calendar, NFC among them) that are not
yet scoped into a task above. A candidate graduates into a numbered task
here only once the process in that doc is followed — a real plugin need
identified, the capability added to RFC 0083's registry centrally, and
permission/privacy/consent defined before implementation starts.

## Related docs

- [ADR 0004](../adrs/0004-shared-device-bridge-contract-with-desktop.md)
- [research 0002](../research/0002-device-capability-candidates.md)
