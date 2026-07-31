# Epic: Shell

> The Capacitor scaffold itself — onboarding, instance persistence, WebView
> loading, navigation policy. This is the reason this repository exists, and
> the direct functional port of the shipped `sovereign-desktop` shell.

## Status

⏳ In Progress — task 20.1 scaffold implemented and partially verified on
iOS Simulator; not yet ✅ (see task detail below for exactly what is and
isn't verified).

## Overview

Everything in this epic is native-shell-only concerns, per
[CONCEPT.md](../../CONCEPT.md) and
[ADR 0002](../adrs/0002-universal-one-binary-distribution.md): first-launch
instance URL onboarding, persistent ordered instance storage, WebView boot
flow, and multi-instance switching. No auth, role, or plugin-permission logic
is duplicated in native code anywhere in this epic.

Largely a **port** of the already-shipped `sovereign-desktop` (Tauri) shell —
see the mapping table in sovereign workstream 0002's leg 4 detail:

| `sovereign-desktop` (shipped)   | `sovereign-mobile` equivalent     |
| ------------------------------- | --------------------------------- |
| `src/onboarding.ts`             | Same flow, Capacitor              |
| `src/store.ts` (`plugin-store`) | `@capacitor/preferences`          |
| `src/main.ts` boot flow         | Identical logic                   |
| `index.html` onboarding UI      | Reusable with minimal change      |
| `.github/workflows/release.yml` | Same shape, iOS/Android artifacts |

## Tasks

#### ⏳ 20.1 — Capacitor shell scaffold

**Goal:** Bootstrap this repo with a working Capacitor shell for iOS and
Android: first-launch instance URL onboarding, persistent instance storage,
WebView loading, navigation policy, and multiple-instance switching.

**Deliverables:**

- Capacitor app scaffold with committed iOS and Android project files.
- First-launch instance URL onboarding UI.
- Persistent ordered instance list storage (`@capacitor/preferences`).
- WebView boot flow: stored instance → load; no stored instance → onboarding.
- Multiple-instance add, remove, and switch flows.
- Primary WebView navigation policy that keeps configured Sovereign instances
  in-app and opens external links outside the shell.
- Local development instructions for iOS Simulator and Android Emulator.
- Document required local tooling: Node, pnpm, Xcode, Android Studio,
  CocoaPods, Capacitor CLI (see [CONTRIBUTING.md](../../CONTRIBUTING.md)).

**Dependencies:** Sovereign RFC 0058. Validates against `/api/health` until
sovereign epic task 20.2 (instance validation endpoint, a `sovereign`
monorepo task) ships — see
[ADR 0002](../adrs/0002-universal-one-binary-distribution.md).

**Technical notes:**

- `server.url` → the remote instance over `https`. Never bundle assets
  behind `capacitor://` — see
  [ADR 0005](../adrs/0005-server-url-not-bundled-assets.md).
- Navigation policy: keep configured instances in the primary WebView; open
  external links in the system browser. Sovereign RFC 0058 requires this
  explicitly.
- **Verify what the shipped desktop shell actually validates against before
  copying it.** Sovereign epic task 17.1's text says `/api/admin/health`
  (admin-key-gated, would 403); sovereign RFC 0038's changelog says it was
  corrected to `/api/health`. Confirm which is current in the shipped
  `sovereign-desktop` code before porting, and note the finding here.
- Offline scope comes from [research 0003](../research/0003-webview-offline-behavior.md)'s
  finding, not from optimism.

**Review checklist — status as of this implementation pass (2026-07-31):**

- ✅ App opens in iOS Simulator — built with `xcodebuild` against the real
  Capacitor 8.4.2 SPM package, installed and launched via `xcrun simctl` on
  two separate simulator instances (iPhone 17, iPhone 17 Pro), confirmed via
  screenshot plus WebKit's own `didGeneratePageLoadTiming` log line (not
  just process-alive).
- ❌ Android Emulator — **not verified in this environment.** `npx cap add
android` succeeded and the Java source compiles against Capacitor's real
  API surface (confirmed by reading the actual `com.getcapacitor` sources
  resolved locally), but `./gradlew compileDebugJavaWithJavac` fails with
  `invalid source release: 21` — this machine has JDK 17, Capacitor Android
  8 requires JDK 21+ (now documented in
  [CONTRIBUTING.md](../../CONTRIBUTING.md)). No Android SDK or emulator is
  installed here either. Needs a properly provisioned Android dev machine.
- ✅ First launch shows instance URL onboarding — confirmed by screenshot:
  title, subtitle, labeled URL input (auto-focused, keyboard shown), submit
  button reading "Connect" for the first-launch case.
- ❌ A valid saved instance loads in the WebView on restart — not exercised;
  needs a reachable Sovereign instance to validate against, not attempted
  this pass.
- ❌ Users can add, remove, and switch between at least two instances — UI
  code is written and unit-adjacent logic (`store.ts`) is exercised only
  indirectly (no direct native-storage integration test); not exercised
  end-to-end on-device.
- ❓ External links do not silently navigate the primary WebView away from
  the configured instance — **the enforcing code compiles successfully**
  (`MainViewController.swift`'s `WKNavigationDelegate` forwarding, see
  [ADR 0007](../adrs/0007-navigation-policy-enforcement.md)) but the actual
  cancel-and-open-externally behavior has not been click-tested on-device.
  Android's equivalent (`NavigationPolicyWebViewClient.java`) is
  compile-unverified (see Android note above).
- ✅ No Sovereign auth, role, or plugin behavior is duplicated in native
  code — true by construction; nothing in this scaffold touches auth.
- ❌ Real **physical device** verification — not done; simulator only, as
  expected at this stage. See Risks.

**Implementation notes from this pass, for whoever picks this up next:**

- The Xcode project uses the classic (non-file-system-synchronized) format:
  new source files must be added to `project.pbxproj` explicitly
  (`PBXBuildFile`, `PBXFileReference`, the `App` group, and the `Sources`
  build phase) or Xcode silently excludes them from the build — this bit us
  once already (`MainViewController.swift` compiled with zero errors from
  Xcode's own `xcodebuild -resolvePackageDependencies` + build, but was
  never actually part of the target until the pbxproj was fixed by hand,
  which only surfaced as a runtime "Unknown class ... in Interface Builder
  file" error and a blank black screen — not a build failure).
- Capacitor 8's `CAPBridgeViewController` does **not** conform to
  `WKNavigationDelegate` itself; it installs a separate concrete
  `WebViewDelegationHandler` (confirmed by reading the resolved
  `Capacitor.swiftinterface`, not assumed). `MainViewController` takes over
  as `navigationDelegate` and forwards everything it doesn't implement back
  to the original concrete handler — see the class doc comment in
  `ios/App/App/MainViewController.swift` for why a concrete-type reference
  was used instead of the `WKNavigationDelegate` protocol existential
  (Swift's `@objc optional` protocol-method dispatch is ambiguous for
  `decidePolicyFor`'s overload set).

## Risks

- **Real-device verification is a documented human handoff.** An agent can
  build, wire CI, and prepare a checklist, but final sign-off requires a
  physical iPhone and Android device — see sovereign workstream 0002's Risks
  section and `docs/pwa-real-device-testing.md` in the `sovereign` monorepo.
- **Apple guideline 4.2 (minimum functionality)** applies to WebView
  wrappers. Well precedented by Nextcloud/Bitwarden/Element, and the
  self-hosted-client category is the one Apple accommodates — but non-zero
  risk, and the one most likely to change this epic's plan.
- **WKWebView data eviction** under storage pressure or prolonged non-use —
  see [ADR 0003](../adrs/0003-cookie-in-webview-auth.md) and
  [research 0003](../research/0003-webview-offline-behavior.md).

## Related docs

- [CONCEPT.md](../../CONCEPT.md)
- [ADR 0001](../adrs/0001-capacitor-as-shell-technology.md),
  [ADR 0002](../adrs/0002-universal-one-binary-distribution.md),
  [ADR 0003](../adrs/0003-cookie-in-webview-auth.md),
  [ADR 0005](../adrs/0005-server-url-not-bundled-assets.md)
- [research 0001](../research/0001-shell-and-bridge-technology-choice.md),
  [research 0003](../research/0003-webview-offline-behavior.md)
