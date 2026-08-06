# Epic: Shell

> The Capacitor scaffold itself — onboarding, instance persistence, WebView
> loading, navigation policy. This is the reason this repository exists, and
> the direct functional port of the shipped `sovereign-desktop` shell.

## Status

⏳ In Progress — task 20.1 scaffold implemented and verified on iOS
Simulator, Android Emulator, and a real physical iPhone, including a real
Android-specific back-navigation bug found and fixed (see task detail
below); not yet ✅ pending physical-device sign-off on Android.

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

**Dependencies:** Sovereign RFC 0058. Validated against `/api/health` until
sovereign epic task 20.2 (instance validation endpoint, a `sovereign`
monorepo task) shipped; now validates against the richer `/api/instance`
endpoint it added — see
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
- ✅ Users can add, remove, and switch between at least two instances —
  verified on both platforms. iOS confirmed 2026-08-02. Android confirmed
  2026-08-06: added `sovereign.openfs.io` and a local `sovereign` dev
  server (`http://10.0.2.2:3000`, the emulator's address for the host's
  `localhost:3000`) as two real instances side by side on a fresh
  `sovereign_mobile_test` AVD; both listed correctly; switched into the
  first (openfs.io, confirmed by no new request landing on the local dev
  server's logs) and then the second (confirmed by fresh `GET /` and
  `GET /api/verify` requests appearing in the local dev server's own log
  at the moment of the tap); removed a stored instance cleanly, returning
  to onboarding. iOS verification: added `sovereign.openfs.io` and a local
  `sovereign` dev server (`http://localhost:3000`) as two real instances
  side by side, confirmed both listed with their real names
  (`sovereign.openfs.io`, `Sovereign`) via `/api/instance`, and removed the
  second cleanly.
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
- ✅ The history-based back-navigation instance-switch affordance
  ([ADR 0006](../adrs/0006-history-based-instance-switch-affordance.md))
  — **confirmed working on both platforms, including the cold-relaunch
  case, as of 2026-08-06.** This entry went through three revisions in one
  session — recorded here in full because the middle revisions are a
  useful record of what *didn't* work and why, not just noise:
  1. First revision (based on user-initiated-navigation testing only)
     declared this fully resolved on Android. **Wrong** — it never tested
     the cold-relaunch path.
  2. Second revision, after specifically testing cold-relaunch (force-stop
     the app, or let Android kill it in the background, then reopen it —
     `boot()`'s automatic `location.assign()` on launch): found it
     reliably **broken**, 8/8 consecutive back-presses failing to reach
     the instance manager on two independent clean installs. Hypothesized
     a lingering IME input connection (`adb shell dumpsys input_method`
     showed the WebView registered as the served view immediately after
     the redirect, before any tap).
  3. **This revision, after actually fixing it**: the IME theory turned
     out to be a correlation, not the cause — clearing WebView focus
     (including with delayed retries, to rule out a hydration-timing
     race) did not fix it. Instrumenting `Activity.dispatchKeyEvent`
     directly revealed the real bug: the WebView's own `canGoBack()`
     reports `false` and `goBack()` silently no-ops in this state, even
     though `copyBackForwardList()` correctly shows two history entries
     with the current index past the first — a genuine WebView/Chromium
     internal-state bug tied to this shell's early, JS-triggered,
     cross-origin redirect on cold launch, not an app logic error.
     **Fixed** by bypassing the broken mechanism entirely:
     `MainActivity.java`'s `dispatchKeyEvent` now checks the WebView's
     current URL directly and, on back-press from a loaded remote
     instance, explicitly `loadUrl()`s the known local manage URL instead
     of trusting `goBack()`. Verified against the exact prior repro —
     clean install, connect, force-stop, relaunch, back-press — across
     three independent clean installs, all succeeding on the first press,
     with no regression to the already-working switch-via-row-tap and
     back-from-that-state paths.
  See [ADR 0006](../adrs/0006-history-based-instance-switch-affordance.md)'s
  Consequences section for the full raw evidence trail.
- ✅ No Sovereign auth, role, or plugin behavior is duplicated in native
  code — true by construction; nothing in this scaffold touches auth (no
  credentials were entered into the loaded instance during testing — a
  human tester supplied test credentials mid-session and was told those
  can't be typed in by the agent, per a hard rule against entering
  credentials into any field).
- 🟡 Real **physical device** verification — **done on iOS (2026-08-06),
  still not done on Android.** Built, signed (`Apple Development:
  kasunben@proton.me`, automatic signing via `-allowProvisioningUpdates`
  once the account was added to Xcode and the resulting developer profile
  explicitly trusted on-device under Settings → General → VPN & Device
  Management), installed, and launched on a real, physically connected
  iPhone 15 Pro ("La Sirena", iOS build 23F84) via `xcrun devicectl`. A
  human tester drove the device directly (this agent has no way to inject
  touches into physical hardware, unlike Simulator/Emulator) and confirmed,
  against the real `sovereign.openfs.io` instance: onboarding shown on
  first launch; connect and real sign-in succeeded; force-quit and
  relaunch loaded straight back into the signed-in instance with no
  onboarding; **a genuine edge-swipe gesture** (not Simulator's synthetic
  gesture injection) returned from the loaded instance to the instance
  manager, the strongest evidence yet for
  [ADR 0006](../adrs/0006-history-based-instance-switch-affordance.md);
  tapping back into the listed instance reloaded it correctly; and an
  external link (from the sign-in page) opened in Safari, staying out of
  the primary app view, confirming
  [ADR 0007](../adrs/0007-navigation-policy-enforcement.md) on real
  hardware too. Not exercised on real hardware: add/remove of a second
  instance (only one real instance was available to this device's
  network), and the cross-origin cancel-and-warn path specifically. See
  Risks — the Android half of this item remains a human handoff.

**Implementation notes from this pass, for whoever picks this up next:**

- **When driving Android back-navigation via `adb shell input keyevent
  KEYCODE_BACK`, make sure no text field has keyboard focus first** — a
  focused `EditText`/WebView input eats the first back-press to dismiss
  the IME (`GoogleInputMethodService` logs it), so a single scripted
  back-press right after typing looks like it did nothing. Relevant to the
  now-fixed cold-relaunch bug's *first* ("inconclusive", 2026-08-02)
  diagnosis, but not the actual root cause of the bug fixed on 2026-08-06
  — see below and ADR 0006 for the full trail.
- **Don't declare a platform-specific navigation/back-button finding
  resolved from only one test shape.** A first correction of the
  2026-08-02 "inconclusive" Android finding tested only user-initiated
  navigation, generalized to "resolved on both platforms," and was wrong
  — cold-relaunch-then-back (arguably the most realistic real-world case,
  since Android kills backgrounded apps routinely) was still broken. When
  a bug report says "sometimes works, sometimes doesn't," the different
  outcomes are probably different code paths, not flakiness — enumerate
  the paths before declaring victory on the first one that works.
- **Don't trust the first plausible-looking correlation as the root
  cause, either — verify it fixes the bug before writing it down as the
  explanation.** `adb shell dumpsys input_method` showing the WebView as
  the IME's served view immediately after the broken cold-relaunch
  redirect looked like a strong lead, and was written up as one — but
  explicitly clearing WebView focus (with delayed retries, to rule out a
  hydration-timing race) didn't fix the actual bug. The real cause, found
  by instrumenting `Activity.dispatchKeyEvent` with logging rather than
  reasoning from `dumpsys` output alone: `WebView.canGoBack()` reports
  `false` and `goBack()` silently no-ops after this shell's specific
  cold-launch redirect pattern, even though `copyBackForwardList()`
  correctly shows a valid previous entry — a genuine WebView/Chromium
  internal-state bug. The fix that actually worked
  (`android/app/src/main/java/fs/sovereign/mobile/MainActivity.java`'s
  `dispatchKeyEvent` override) bypasses `goBack()`/`canGoBack()` entirely
  rather than trying to prevent the stale state from occurring.
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
- **Building and installing to a real, physically connected iPhone from
  the command line** (as opposed to Simulator, or opening Xcode's GUI)
  needs a few things lined up that aren't obvious from a simulator-only
  workflow: (1) an Apple ID must be signed into Xcode's own Accounts
  settings — a valid codesigning identity in Keychain Access isn't
  enough on its own, `xcodebuild` reported "No Account for Team" until
  this was done, and this step needs a human since it requires entering
  a password/2FA; (2) even with automatic signing (`CODE_SIGN_STYLE =
  Automatic` in the project, no `DEVELOPMENT_TEAM` hardcoded), the build
  command still needs `-allowProvisioningUpdates` or it fails with "No
  profiles for '\<bundle id\>' were found" instead of generating one; (3)
  after install, the very first launch attempt via
  `xcrun devicectl device process launch` fails with "invalid code
  signature, inadequate entitlements or its profile has not been
  explicitly trusted" — this isn't a build problem, it's iOS requiring
  the human to go to Settings → General → VPN & Device Management on the
  phone itself and explicitly trust the developer profile before
  anything signed with it will run. None of this is scriptable around;
  each of the three needs a human in the loop once. The working command
  sequence: `xcodebuild -project ios/App/App.xcodeproj -scheme App
  -destination 'id=<device-udid>' -allowProvisioningUpdates build`, then
  `xcrun devicectl device install app --device <device-udid> <path-to-.app>`,
  then (after the on-device trust step) `xcrun devicectl device process
  launch --device <device-udid> fs.sovereign.mobile`.
- **This agent has no way to inject touches or read the screen of a
  physical device**, unlike Simulator (`simctl`) or Emulator (`adb input`
  / `adb exec-out screencap`). Real-device UI verification is therefore a
  live back-and-forth: the agent drives build/install/launch, a human
  performs each tap/gesture on the actual hardware and reports what they
  saw, one step at a time.

## Risks

- **Real-device verification is a documented human handoff.** An agent can
  build, wire CI, and prepare a checklist, but driving the actual taps and
  gestures requires a human with the physical device in hand — see
  sovereign workstream 0002's Risks section and
  `docs/pwa-real-device-testing.md` in the `sovereign` monorepo. **Partly
  discharged for iOS (2026-08-06):** with a real iPhone connected over USB
  and a human tester relaying what they saw, this agent handled the build/
  sign/install/launch side (`xcodebuild -allowProvisioningUpdates` +
  `xcrun devicectl`) while the human performed every tap and gesture — see
  the physical-device checklist entry above. **Android physical-device
  verification is still fully open** — no real Android device was
  available this session.
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
