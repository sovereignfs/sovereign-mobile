# Store listing & release metadata

Text/copy deliverables for task 20.4 ([docs/epics/release.md](epics/release.md)),
drafted so they're ready to paste into App Store Connect and Play Console.
Icons and splash screens (also part of 20.4) are handled separately — see
[resources/](../resources) and `scripts/generate-app-assets.mjs`.

**Scope note**: everything in this doc is text an agent can draft from the
existing codebase and docs. It does **not** cover feature graphic design or
screenshots (blocked on product/design decisions — see ROADMAP), or the
account-dependent steps called out in [Still blocked](#still-blocked-human-handoff)
below (Apple Developer Program / Google Play Console enrollment, actual
submission).

## 1. Listing copy

Grounded in [CONCEPT.md](../CONCEPT.md)'s framing and the existing PWA
manifest copy (`sovereign/runtime/public/manifest.json`), so the mobile
listing doesn't invent a different voice from what's already shipped.

### App name

**Sovereign** (matches `CFBundleDisplayName` / PWA `short_name`). Flagging,
not deciding: this is a short, generic word — worth a trademark/App Store
search-collision check before submission, since that's not something I can
verify from inside this repo.

### Subtitle (iOS, ≤30 chars) / short description (Play, ≤80 chars)

> Your self-hosted workspace.

27 characters — fits both fields as-is, and is a direct reuse of the PWA
manifest's `description` field.

### Full description (≤4000 chars both stores)

> Sovereign is a self-hosted workspace — you run it on your own server, you
> own your data, and no company sits in the middle. This app is the native
> Sovereign client: enter the address of your own Sovereign instance, and
> the app connects directly to it. There's no separate account with us,
> because there is no "us" in the middle — your instance is the whole
> product.
>
> This app doesn't come with a fixed backend or a bundled workspace. You
> (or whoever runs your Sovereign instance) control what plugins, data, and
> features are available. The app itself is a thin, native shell: it
> handles connecting to your instance, remembering it between launches, and
> letting you add and switch between multiple instances if you use more
> than one.
>
> Requires an existing self-hosted Sovereign instance to connect to.
> Sovereign is open source — see https://github.com/sovereignfs/sovereign
> to set one up.
>
> This app does not collect analytics or telemetry, and does not send your
> data anywhere except the instance URL you provide.

~1000 characters — well under budget on both stores if more detail (plugin
ecosystem, specific features) gets added later.

### Keywords (iOS, ≤100 chars, comma-separated, no spaces after commas)

> self-hosted,workspace,productivity,privacy,open source,sovereign,personal server

97 characters.

### Category

Productivity (matches the PWA manifest's `categories: ["productivity"]`).

### Support / marketing URLs

Both stores require a support URL; App Store Connect also wants a marketing
URL and Play Console a privacy policy URL. Not drafted here — these need to
point at real, live pages (a support contact/issue tracker, and a privacy
policy — see [§2](#2-privacy-labels--data-safety-declarations) for what that
policy needs to say), which is a hosting/ownership decision outside this
repo's scope.

## 2. Privacy labels / data safety declarations

Grounded in what the shell actually does, verified against the code, not
assumed:

- **No telemetry or analytics by default** — explicit position in
  [sovereign RFC 0058](https://github.com/sovereignfs/sovereign/blob/main/docs/rfcs/0058-native-mobile-app-shell.md):
  "The shell should not introduce telemetry by default. Any future crash
  reporting or analytics must follow Sovereign's privacy-first posture and
  be opt-in or operator-controlled." Nothing in this codebase contradicts
  that — no analytics SDK, no crash reporter, is integrated.
- **Only two native capabilities exist today**
  ([BridgeCapabilities.java](../android/app/src/main/java/fs/sovereign/mobile/BridgeCapabilities.java),
  [Bridge.swift](../ios/App/App/Bridge.swift)): `haptics.impact` (no data,
  no permission) and `notifications.native` (local notifications only —
  not push/remote; requires the OS notification permission, requested
  inline at point of use). No camera, location, contacts, microphone, or
  any other sensitive capability is implemented.
- **What the app stores locally**: the list of instance URLs the user has
  added (`@capacitor/preferences`, on-device only) and the session cookie
  for whichever instance is currently loaded (browser/WebView cookie jar,
  per-origin — see
  [ADR 0003](adrs/0003-cookie-in-webview-auth.md)). Neither leaves the
  device except to the instance origin the user themselves entered.
- **Network access**: the app talks to exactly one destination — the
  instance origin the user enters — for validation
  (`GET /api/instance`) and then for all WebView content thereafter. No
  other network destination is contacted by shell code.

### Apple App Privacy ("nutrition label") — App Store Connect

| Question                    | Answer                                                                                                                                                                                           |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Data collected by this app  | **None** (the app itself collects nothing; any data handling happens on the user's own instance, outside Apple's App Privacy scope, exactly like Safari isn't asked to declare what websites do) |
| Data linked to the user     | N/A                                                                                                                                                                                              |
| Data used to track the user | No                                                                                                                                                                                               |

### Google Play Data Safety form

| Section                                                             | Answer                                                                                                                                                          |
| ------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Does your app collect or share any of the required user data types? | **No**                                                                                                                                                          |
| Is all user data encrypted in transit?                              | Yes — the app enforces `https://` (or explicit `http://` only if the user types it, e.g. a LAN dev instance — see [`normalizeInstanceUrl`](../src/validate.ts)) |
| Does your app allow users to request data deletion?                 | Not applicable from the app itself — deletion is a function of the user's own instance, not this shell                                                          |

### Privacy policy page (required by both stores)

Needs a real, hosted page — drafting the _content_ here since that's
groundable, even though hosting it isn't this repo's job:

> This app does not collect, store, or transmit any personal data to
> [org/maintainer name]. It connects only to the self-hosted Sovereign
> instance URL you provide, which you or your organization control. Any
> data handling that occurs happens on that instance, under its own
> privacy policy — not this app's. Locally, the app stores only the list
> of instance URLs you've added and your session cookie for the
> currently-connected instance, both on-device only.

## 3. Minimum supported OS versions

[Sovereign RFC 0058](https://github.com/sovereignfs/sovereign/blob/main/docs/rfcs/0058-native-mobile-app-shell.md)
leaves this as an explicit open question ("Minimum supported iOS and
Android versions") with no stated rationale either way — this is a genuine
decision for you to confirm, not something I should silently pick.

**What's already configured** (Capacitor/Ionic scaffold defaults, not a
deliberate product decision until now):

- iOS: `IPHONEOS_DEPLOYMENT_TARGET = 15.0`
- Android: `minSdkVersion = 24` (Android 7.0, `targetSdkVersion = 36`)

**My recommendation: keep both as-is.** Reasoning:

- Nothing in this codebase depends on an iOS or Android API newer than
  what these floors already provide — the bridge transport's
  `WebViewFeature.isFeatureSupported()` checks
  ([NavigationPolicyWebViewClient.java](../android/app/src/main/java/fs/sovereign/mobile/NavigationPolicyWebViewClient.java))
  already degrade gracefully on older WebView builds rather than requiring
  a version floor.
- These floors have already been built and tested this cycle, including
  end-to-end verification on a real physical iPhone (2026-08-06) and
  Android Emulator — raising either floor now would be undoing tested
  configuration for no stated reason, and lowering either would be
  untested.
- iOS 15+ and Android 7+ are both old enough to cover the large majority of
  actively-used devices per each platform's own public distribution
  data — I'm not citing a specific percentage since I can't verify current
  figures from inside this repo, but neither floor is aggressively new.

If you want a different floor, it's a one-line change in each project
(`IPHONEOS_DEPLOYMENT_TARGET` / `variables.gradle`'s `minSdkVersion`), not
a structural one.

## 4. Store-review checklist

For whoever submits, and to pre-empt reviewer questions about an app that's
"just a URL box" on first launch:

- [ ] **Reviewer notes / demo instance**: both stores let you attach notes
      for the human reviewer. Since this app requires a real Sovereign
      instance to do anything, **provide a working demo instance URL and
      test credentials in the reviewer notes** — without one, a reviewer
      with no self-hosted Sovereign instance cannot get past onboarding
      and may reject for "incomplete functionality."
- [ ] **Permissions requested, and why** (for both stores' permission
      justification fields):
  - `android.permission.INTERNET` — required for all WebView content;
    Android does not surface this to users as a runtime prompt.
  - `android.permission.VIBRATE` — backs `haptics.impact`; not user-facing
    as a permission prompt either.
  - `android.permission.POST_NOTIFICATIONS` (Android 13+) / iOS
    notification permission — backs `notifications.native` (local
    notifications only, not push). Requested inline, not at launch — see
    [BridgeCapabilities.java](../android/app/src/main/java/fs/sovereign/mobile/BridgeCapabilities.java).
  - No camera, location, contacts, microphone, or other sensitive
    permission is requested by anything in this repo.
- [ ] **External links leave the app**: same-origin content (the connected
      instance) stays in-app; cross-origin links open in the system
      browser — see [ADR 0007](adrs/0007-navigation-policy-enforcement.md).
      Reviewers sometimes flag apps that trap all navigation in-app; this
      one explicitly doesn't.
- [ ] **No login/account system of our own**: there's nothing to explain
      about account creation, deletion, or data export from _this app_ —
      auth happens entirely on the user's own instance. Store review
      guidelines that require an in-app account-deletion path (Apple
      guideline 5.1.1(v)) don't apply the normal way; be ready to explain
      this if flagged, pointing at the demo instance's own account
      settings.
- [ ] **Apple guideline 4.2 (minimum functionality)** risk: flagged
      previously in [docs/epics/shell.md](epics/shell.md)'s Risks section —
      a WebView wrapper needs the self-hosted-client precedent (Nextcloud,
      Bitwarden, Element) to be persuasive; the reviewer notes/demo
      instance above are the main mitigation.
- [ ] **Offline behavior**: not yet fully scoped (see
      [research 0003](research/0003-webview-offline-behavior.md)) —
      confirm what happens with no network before submission, since an
      unhandled blank screen on connection loss is a common rejection
      reason.

## Still blocked (human handoff)

Not something an agent should attempt — these need real accounts, payment,
and identity verification:

- **Apple Developer Program enrollment** ($99/year) — required before an
  App Store Connect app record can even be created.
- **Google Play Console developer account** ($25 one-time) — same, for
  Play Console.
- **Production signing**: this session's iOS verification used automatic
  _development_ signing (`Apple Development: ...`) for a real-device test
  build — a **distribution** certificate + provisioning profile (or App
  Store Connect API key for CI) is a separate, later step once the
  developer account exists.
- **Actual submission and review** — TestFlight internal testing / Play
  internal testing track, then public release, both require the app
  record and signing above to exist first.
