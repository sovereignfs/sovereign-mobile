---
status: Accepted
date: 2026-07-31
deciders: kasunben
---

# ADR 0007 — Navigation policy enforced by a thin native WebView delegate

## Status

Accepted. Implements sovereign RFC 0058's explicit navigation-policy
requirement; the mechanism itself is decided in this repo since Capacitor
has no built-in plugin for it.

## Context

RFC 0058 requires: "The shell loads only user-configured Sovereign instances
in the primary WebView. External links should open in the platform browser
or an approved in-app browser surface instead of silently navigating the
shell away from the configured instance." The active instance's origin is
chosen by the user at runtime (per
[ADR 0002](0002-universal-one-binary-distribution.md)), so this cannot be a
static, build-time allow-list — Capacitor's `server.allowNavigation` config
doesn't fit, since it's fixed at build time and this shell has no fixed
instance to list.

## Decision

A thin native delegate on each platform intercepts WebView navigation and
decides same-origin (allow, stays in the WebView) vs. cross-origin (cancel,
open via the system browser instead):

- **iOS:** `MainViewController.swift` (a `CAPBridgeViewController` subclass,
  wired in via `Main.storyboard`'s `customClass`) takes over as
  `webView.navigationDelegate` itself and implements
  `webView(_:decidePolicyFor:decisionHandler:)`, comparing the navigation's
  target origin against the shell's currently active instance origin. It
  keeps a typed reference to Capacitor's own original
  `WebViewDelegationHandler` and forwards every selector it doesn't
  implement back to it (standard Foundation proxy-forwarding via
  `responds(to:)`/`forwardingTarget(for:)`) so Capacitor's own navigation
  lifecycle handling keeps working for everything this class doesn't care
  about. Cross-origin loads are opened via `UIApplication.shared.open(_:)`.
- **Android:** `NavigationPolicyWebViewClient.java` (a `BridgeWebViewClient`
  subclass, installed from `MainActivity.java`'s overridden `load()`) does
  the same origin comparison in `shouldOverrideUrlLoading`. Cross-origin
  loads are opened via an `ACTION_VIEW` `Intent`.

The active origin is read from `@capacitor/preferences`' `sovereign.activeUrl`
key (the same storage `store.ts` already writes), so native code has no
separate source of truth to keep in sync — it reads exactly what the TS
shell already persists.

### A same-origin load must be decided directly, never forwarded

The load-bearing detail, found only by testing against a real instance (see
Verified below): for the same-origin case, **do not** forward the decision
to Capacitor's own original handler (iOS: `WebViewDelegationHandler`;
Android: `BridgeWebViewClient.shouldOverrideUrlLoading` via `super`, which
calls `Bridge#launchIntent()`). Both of Capacitor's own defaults compare the
navigation's host against the app's _configured_ host (`localhost` / the
bundled local scheme) and the static `server.allowNavigation` list — neither
of which has any notion of this shell's runtime-chosen active instance. The
practical effect: forwarding the same-origin case sent the very instance
this shell exists to load out to the system browser instead of the app's
own WebView, on both platforms, via the identical mechanism. This shell must
call `.allow` / return `false` directly for the same-origin case on both
platforms — see Verified below for exactly how this was caught.

## Rejected alternatives

- **Static `server.allowNavigation` config.** Doesn't support a runtime-
  chosen origin; would require a rebuild per instance, contradicting
  [ADR 0002](0002-universal-one-binary-distribution.md).
- **No enforcement; rely on the loaded instance's own link `target`/`rel`
  behavior.** Not this shell's call to make — a plugin author inside the
  loaded instance could link anywhere, and RFC 0058's requirement is
  explicit that the shell, not the instance, owns this policy.

## Consequences

- Two small, platform-native files carry real logic for the first time in
  this repo — acceptable per
  [AGENTS.md](../../AGENTS.md)'s "native code only for glue a Capacitor
  plugin doesn't already cover" rule, since no plugin covers this.
- Both native delegates must be kept in sync with any future change to how
  or where the active origin is stored — a change to `store.ts`'s
  `KEY_ACTIVE_URL` key name breaks native enforcement silently unless
  both platform files are updated in the same change.
- **Verified on iOS Simulator against a real instance (2026-08-01),
  `sovereign.openfs.io`.** The first implementation had exactly the
  forwarding bug described above: connecting to a real instance from
  onboarding sent it to Safari instead of loading it in-app. Root-caused by
  reading Capacitor's actual resolved `Capacitor.swiftinterface` (not
  guessed) — `CAPBridgeViewController` doesn't conform to
  `WKNavigationDelegate` itself, Capacitor installs a separate
  `WebViewDelegationHandler`, and _that_ object's default policy is what
  redirected the load externally once this shell's delegate forwarded to
  it. Fixed by deciding `.allow` directly for the same-origin case (see
  above) rather than forwarding. Re-verified after the fix: connect, app
  restart (loads saved instance directly), remove, re-add, and switch
  (tapping an existing instance row) all now correctly stay in this
  shell's own WebView.
- **Verified on Android Emulator against the same real instance
  (2026-08-02).** JDK 21 and the Android SDK/emulator (arm64-v8a,
  API 34) were installed directly (not via Homebrew — this machine's
  Homebrew Cellar had broken directory ownership requiring a `sudo chown`
  only the machine owner can run) into this repo's gitignored
  `.toolchain/`. `./gradlew assembleDebug` succeeded, and the identical
  forwarding fix (`NavigationPolicyWebViewClient.java` returning `false`
  directly instead of forwarding to `super`/`Bridge#launchIntent()`) was
  confirmed correct at runtime, not just by code inspection: connect,
  restart (loads saved instance directly), and the same-origin case
  staying in-app (`topResumedActivity` / `dumpsys` confirmed
  `fs.sovereign.mobile/.MainActivity` stayed foreground, not a browser)
  were all reproduced on-device, matching iOS.
- **Cross-origin click-through verified on a real physical iPhone
  (2026-08-06).** Built, signed, installed, and launched on a connected
  iPhone 15 Pro via `xcodebuild -allowProvisioningUpdates` + `xcrun
  devicectl`; a human tester tapped an actual external link (from
  `sovereign.openfs.io`'s real sign-in page) and confirmed it opened in
  Safari, staying out of the primary app view — closing the "still not
  verified" gap noted above, at least for iOS. The same tester also
  reconfirmed the same-origin path end-to-end on real hardware: onboarding,
  connect, sign-in, force-quit/relaunch persistence, and switching back
  into the listed instance all stayed in-app as expected. **Still not
  verified:** the equivalent cross-origin click-through on a real Android
  device (no physical Android device was available this session).
