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

**Review checklist — status as of 2026-08-02, tested against a real
instance (`sovereign.openfs.io`) on both iOS Simulator and Android
Emulator:**

- ✅ App opens in iOS Simulator — built with `xcodebuild` against the real
  Capacitor 8.4.2 SPM package, installed and launched via `xcrun simctl` on
  two separate simulator instances (iPhone 17, iPhone 17 Pro).
- ✅ App opens in Android Emulator — this environment had no JDK 21 or
  Android SDK at all; both were installed directly into this repo's
  gitignored `.toolchain/` (not via Homebrew — this machine's Homebrew
  Cellar has broken directory ownership requiring a `sudo chown` only the
  machine owner can run, so JDK 21 and the SDK cmdline-tools were
  downloaded and extracted directly instead). `./gradlew assembleDebug`
  **succeeded** against the real Capacitor Android 8.4.2 API. Built,
  installed, and launched on a freshly created arm64-v8a API 34 AVD via
  `adb`.
- ✅ First launch shows instance URL onboarding — confirmed by screenshot
  on both platforms, and by successfully connecting to a real instance on
  both.
- ✅ A valid saved instance loads in the WebView on restart — verified on
  both platforms: force-stopped and relaunched the app after connecting;
  it loaded `sovereign.openfs.io`'s real sign-in page directly, no
  onboarding shown.
- 🟡 Users can add, remove, and switch between at least two instances —
  **fully verified on iOS** (Connect/add, ✕/remove, row-tap/switch all
  confirmed). **Only partially verified on Android**: add (Connect) and
  the underlying same-origin navigation are confirmed; remove and
  row-tap/switch could not be exercised because reaching the instance
  manager a second time depends on back-navigation, which is the
  unresolved item below. Only one real instance was available to test
  with on either platform, so "switch **between** two" specifically wasn't
  exercised anywhere — the underlying code path (`setActiveUrl` +
  `loadInstance`) is identical regardless of how many instances are
  stored.
- ✅ External links do not silently navigate the primary WebView away from
  the configured instance — **found and fixed a real bug in the process,
  confirmed on both platforms**: see
  [ADR 0007](../adrs/0007-navigation-policy-enforcement.md)'s Verified
  section. The same-origin case was being forwarded to Capacitor's own
  default handler on both iOS and Android, which redirected it externally
  — the exact opposite of what RFC 0058 requires. Fixed and re-verified on
  both: connect, restart, and (on iOS) remove/re-add/switch all correctly
  stay in-app; on Android, `dumpsys activity activities` confirmed
  `fs.sovereign.mobile/.MainActivity` stayed the foreground activity
  through the same flow. The cross-origin (cancel + open externally)
  branch itself is still not click-tested against a real external link on
  either platform.
- 🟡 The history-based back-navigation instance-switch affordance
  ([ADR 0006](../adrs/0006-history-based-instance-switch-affordance.md))
  — **confirmed working on iOS**, repeatably. **Inconclusive on Android**:
  testing via `adb shell input keyevent KEYCODE_BACK` and an edge-swipe
  gesture produced inconsistent results (one run reached the app and then
  hung on a native plugin call, coinciding with the emulator's WebView
  renderer process being killed/respawned in the logs; several other runs
  never reached the app at all, apparently consumed by the system IME
  layer first). See ADR 0006's Consequences section for the full detail —
  this needs a real device or a more interactive test session to resolve,
  not headless `adb input` injection.
- ✅ No Sovereign auth, role, or plugin behavior is duplicated in native
  code — true by construction; nothing in this scaffold touches auth (no
  credentials were entered into the loaded instance during testing — a
  human tester supplied test credentials mid-session and was told those
  can't be typed in by the agent, per a hard rule against entering
  credentials into any field).
- ❌ Real **physical device** verification — not done; simulator/emulator
  only, as expected at this stage. See Risks.

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
- **Never forward a same-origin navigation decision to Capacitor's own
  default handler on either platform** — see
  [ADR 0007](../adrs/0007-navigation-policy-enforcement.md). Its defaults
  assume the app's real content is bundled locally and will redirect a
  runtime-chosen remote origin externally, which is exactly backwards for
  this shell.
- **The local onboarding page needs explicit `pageshow`/`back_forward`
  handling to work when returned to via back-navigation** — a bfcache
  restore doesn't re-run `main.ts`'s boot script, and even a fresh reload
  of it would immediately redirect forward again without this. See
  [ADR 0006](../adrs/0006-history-based-instance-switch-affordance.md).
- **When testing interactively via the iOS Simulator MCP tool, `tap`/`swipe`
  coordinates are in _points_ (e.g. 402×874 for iPhone 17 Pro — the tool
  states the exact space per device), not screenshot pixels.** Using
  pixel-scale coordinates from a viewed screenshot (which may itself be
  further downscaled for display) silently taps outside the visible area
  or the wrong element with no error — this cost real time before being
  diagnosed by comparing a screenshot's actual `sips`-reported pixel
  dimensions against the tool's stated point space. `adb shell input tap`
  on Android, by contrast, wants raw device pixels directly (e.g.
  1080×2400), not points — the two toolchains don't share a coordinate
  convention.
- **Getting Android building at all required bypassing Homebrew entirely**
  in this environment: its Cellar had broken directory ownership
  (`brew install` demanded a `sudo chown` this agent can't run). JDK 21
  (Eclipse Temurin, via Adoptium's API) and the Android SDK cmdline-tools
  (Google's direct zip distribution) were downloaded and extracted
  straight into this repo's `.toolchain/` (gitignored, ~6GB) instead —
  `sdkmanager`/`avdmanager`/`gradlew` all invoked with `JAVA_HOME`/
  `ANDROID_HOME` pointed at that directory rather than any system
  install. `android/local.properties` (gitignored) points `sdk.dir` at
  the same place. None of this is portable to another machine as-is; a
  real dev environment should just install JDK 21 and Android Studio
  normally.
- **Capacitor's Android bridge logs plugin calls and console messages to
  logcat by default** (tags `Capacitor`, `Capacitor/Console`,
  `Capacitor/Plugin`) — genuinely useful for headless debugging without
  Chrome remote-debugging, which isn't available in a fully headless
  environment. `adb logcat -d | grep -iE "chromium|console|capacitor"` was
  how both the navigation-policy bug and the back-navigation hang were
  actually diagnosed here, not guessed at.

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
