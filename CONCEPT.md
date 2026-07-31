# Sovereign Mobile

## Concept Paper (Draft v0.1)

### Vision

Sovereign Mobile is the native iOS and Android client for
[Sovereign](https://github.com/sovereignfs/sovereign), the self-hostable
workspace runtime. It is a **minimal Capacitor shell**, not a second
implementation of the platform: on first launch it asks the user for the URL
of their own self-hosted Sovereign instance, validates it, persists it, and
loads the full Sovereign workspace — auth, plugins, shell layout, everything —
in a native WebView. One binary is published to the App Store and the Play
Store; it works against any self-hosted instance, not a fixed backend.

On top of that thin WebView shell sits a **device bridge**: a narrow,
versioned channel that lets web content already running inside the WebView
(the platform itself, and any plugin it hosts) reach native device
capabilities — geolocation, camera, calendar, NFC, haptics, push, and
whatever else a plugin legitimately needs — that browser Web APIs don't
cover or don't cover well enough inside a WebView. Plugin authors never
touch Capacitor directly; they call one portable SDK surface
(`sdk.device.*`), and that surface silently routes to the bridge only when
running inside this shell, falling back to standard Web APIs everywhere
else.

### The problem

Sovereign v1 ships as an installable PWA. That covers the "install from the
browser" path, but it does not give self-hosters a listing in the App Store
or Play Store, and a PWA cannot reach a meaningful slice of native
capability inside a WebView — reliable push notifications, biometric
unlock, a native photo/document picker, calendar and contacts integration,
NFC, background work. Users who expect "a real app" don't get one, and
plugin authors who need any of the above have no portable way to ask for
it.

Sovereign's product model is self-hosted and plugin-first. A native mobile
app that reimplements auth, routing, or plugin hosting natively would fork
the platform into a second, drifting implementation — exactly what the
existing desktop shell (`sovereign-desktop`, Tauri) was built to avoid. The
same discipline has to hold for mobile.

### The solution

Two layers, kept deliberately separate:

1. **The shell.** A Capacitor app that does nothing but onboard an instance
   URL, persist a list of instances, and load the active one into a
   WKWebView / Android System WebView. It owns store presence, native
   permission declarations, and WebView lifecycle — nothing about auth,
   plugins, or layout. This is a close port of the already-shipped
   `sovereign-desktop` Tauri shell; the two follow the same architectural
   pattern (Nextcloud/Bitwarden/Element-style thin native wrapper) so a
   contributor who knows one recognizes the other.

2. **The bridge.** A capability-negotiated channel, shared with
   `sovereign-desktop`, that plugins reach through `sdk.device.*` alone.
   The shell advertises which capabilities it implements and at which
   version; plugins ask, get a typed result (available / denied / dismissed
   / unavailable), and never branch on Capacitor or on which native shell
   they happen to be running inside. Browser and PWA users keep working
   through the plain Web API fallback tier — nothing about a plugin's code
   changes because it happens to run inside this shell.

### Core principles

**Not a second platform.** Everything user-facing — auth, roles, sessions,
plugin routing, shell layout, CSP — is served by the user's own instance,
unchanged. If a feature could live in the instance instead of the shell, it
must.

**One binary, any instance.** No build-per-deployment. The same App
Store/Play Store binary works against any self-hosted Sovereign instance the
user points it at, mirroring the desktop shell's model and self-hosting's
whole premise: users own their deployment, the client doesn't assume a
fixed backend.

**Bridge, not a fork.** Device capability access is additive plumbing on
top of the existing plugin/SDK contract, not a parallel native plugin
system. A plugin that calls `sdk.device.getLocation()` runs unchanged in a
browser, an installed PWA, and this shell — the SDK detects where it's
running and routes accordingly.

**Capability, not trust-by-default.** Nothing native is reachable from web
content by default. Each device capability is a named, versioned entry a
plugin must declare, request, and (per RFC 0083) be user-consented into
before a call resolves — never a blanket "this app can use the camera"
toggle inherited by every plugin that happens to be installed.

**No shell-side product logic.** The shell renders one thing: the user's
own instance. Store-facing behavior (privacy labels, permission strings) has
to be literally true of what the shell does, not aspirational copy.

### Relationship to the wider ecosystem

`sovereign-mobile` is a sibling repository to the `sovereign` monorepo, not
a package inside it — same relationship `sovereign-desktop` already has.
Its own governing design lives centrally:

- [sovereign RFC 0058](https://github.com/sovereignfs/sovereign/blob/main/docs/rfcs/0058-native-mobile-app-shell.md)
  — the shell's design (this repo's scope, distribution model, onboarding
  flow, device API tiering).
- [sovereign RFC 0083](https://github.com/sovereignfs/sovereign/blob/main/docs/rfcs/0083-device-bridge-capability-contract.md)
  — the bridge protocol (`@sovereignfs/bridge` + `@sovereignfs/sdk/device-client`),
  shared with `sovereign-desktop` so the two shells cannot drift.
  `sovereign-mobile` implements the Capacitor **transport** of that
  contract; it does not own the protocol.
- [sovereign RFC 0080](https://github.com/sovereignfs/sovereign/blob/main/docs/rfcs/0080-plugin-surface-model.md)
  — `sdk.device.getSurface()` and the manifest `surfaces` field, the
  mechanism behind "some plugin features only show up inside a native
  shell." Kept deliberately separate from capability/permission checks.
  Referenced here because this shell is one of the two surfaces (`mobile`,
  alongside `desktop`) that model answers.
- [sovereign epic 20](https://github.com/sovereignfs/sovereign/blob/main/docs/epics/mobile.md)
  and [workstream 0002](https://github.com/sovereignfs/sovereign/blob/main/docs/workstreams/0002-native-mobile-app-release.md)
  — the task breakdown and release sequencing that created this repository.

This repo owns only what's native-shell-specific: the Capacitor scaffold,
iOS/Android project files, onboarding UI, instance persistence, WebView
navigation policy, native permission declarations, store release metadata,
and the shell-side implementation of the shared bridge transport. Decisions
about the bridge _protocol_ itself, the `sdk.device.*` surface, and
platform-side consent UI are made in `sovereign` and consumed here, not
re-decided.

**Distinct from `sovereign-edge`.** A separate, unrelated repository in this
workspace — a standalone, fully offline on-device AI app with its own React
Native UI and its own inference/connector stack. It shares no code, no
users, and no architecture with this repo beyond both being native mobile
apps. Do not conflate the two.

### Device API strategy

Three tiers, in order of preference (from sovereign RFC 0058, restated here
because it's this repo's central design constraint):

| Tier              | What                                               | Examples                                                                        | Works outside this shell? |
| ----------------- | -------------------------------------------------- | ------------------------------------------------------------------------------- | ------------------------- |
| Web APIs          | Standard browser APIs, already work in a WebView   | `navigator.geolocation`, `getUserMedia`, Web Push where supported               | Yes                       |
| Capacitor plugins | Native code, reached only from inside this shell   | Native photo picker, calendar/contacts, NFC, APNs/FCM push, biometrics, haptics | No                        |
| `sdk.device.*`    | SDK-level abstraction; detects environment, routes | `sdk.device.getLocation()`, `sdk.device.capturePhoto()`, future calls           | Yes, with fallback        |

Plugin authors write to `sdk.device.*` only. NFC, calendar, geolocation, and
camera are all instances of the same pattern — each is an additive capability
registry entry plus a transport implementation on each shell, not a new
mechanism. Sovereign RFC 0083 ships a deliberately thin v1 slice
(`haptics.impact`, `notifications.native`) to prove the contract; broader
capabilities (camera, calendar, NFC, geolocation, biometrics) land afterward
as the same additive pattern, sequenced by product need rather than shipped
speculatively.

### Non-goals

Sovereign Mobile is not:

- A second implementation of Sovereign's runtime, auth, or plugin system.
- A fixed-backend app — it has no built-in instance, and never will.
- A vehicle for native-only product features that don't also work, in some
  form, in a browser. Anything shell-exclusive belongs in the platform's
  `surfaces` availability model (RFC 0080), decided deliberately, not by
  what happens to be easy to build natively.
- A general native plugin-loading platform for third parties. The bridge
  exposes a fixed, versioned capability registry that the platform defines;
  it is not a way for a plugin to ship arbitrary native code.

### Phasing

1. **Phase 1 — whole-instance shell.** Onboarding, instance persistence and
   switching, WebView loading, navigation policy, store release on both
   platforms. No device bridge yet beyond what RFC 0083's thin v1 slice
   ships. This is [workstream 0002](https://github.com/sovereignfs/sovereign/blob/main/docs/workstreams/0002-native-mobile-app-release.md)'s
   scope and this repo's initial reason to exist.
2. **Phase 2 — bridge transport.** Implement this shell's side of
   `@sovereignfs/bridge`: handshake, capability negotiation, the v1 thin
   slice (`haptics.impact`, `notifications.native`), and the consent UX the
   platform's `plugins/account` surface drives.
3. **Phase 3 — capability breadth.** Add device capabilities behind the same
   bridge contract as product need identifies them — geolocation, camera,
   calendar, NFC, background work among them — each gated by its own
   permission string, privacy declaration, and (per plugin) explicit user
   consent grant.

Nothing in Phase 3 is scoped or committed yet; see
[docs/research/0002-device-capability-candidates.md](docs/research/0002-device-capability-candidates.md)
for the open list.

### Long-term vision

A user should be able to search the App Store or Play Store for "Sovereign,"
install one app, point it at their own self-hosted deployment, and get
everything their browser gives them today plus what only a native shell
can: reliable push, a native camera/photo/calendar picker, biometric
unlock, NFC where a plugin needs it — all through the same portable
`sdk.device.*` surface plugin authors already use, so nothing about a
plugin's code has to fork the moment it wants to run well on a phone.
