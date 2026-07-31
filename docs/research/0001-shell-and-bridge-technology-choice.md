# Research 0001 — Shell and bridge technology choice

**Status:** Decided — see [ADR 0001](../adrs/0001-capacitor-as-shell-technology.md)
and [ADR 0004](../adrs/0004-shared-device-bridge-contract-with-desktop.md).

## Question

What native shell technology should `sovereign-mobile` use, and how should
it expose device capabilities to plugin authors without forking the
platform into a second implementation?

## Options considered

### Shell framework

| Option         | Notes                                                                                                                                                                                                                                                           |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Capacitor      | TypeScript-first; WKWebView / Android System WebView; plugin bridge model built for exactly this shape of app. Matches `sovereign-desktop`'s Tauri model conceptually (thin native wrapper, WebView content).                                                   |
| Hotwire Native | Requires separate native bridge code per platform (Swift + Kotlin), working against the goal of one shared TypeScript shell logic layer.                                                                                                                        |
| React Native   | Rebuilds the UI as a second native client rendered outside the WebView — the opposite of "load the user's instance unchanged." Appropriate for `sovereign-edge` (a genuinely native, standalone app with its own UI) but not for a thin instance-loading shell. |

### Device capability exposure

| Option                                                                | Notes                                                                                                                                                                                                               |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Shared bridge contract, designed once, consumed by both native shells | One capability registry, one negotiation protocol, one place a new capability is added. Chosen — see sovereign RFC 0083.                                                                                            |
| Bridge designed independently per shell repo                          | Cheaper short-term per repo, but the two shells diverge the first time a capability ships to only one of them, and plugin authors lose the "write once, run everywhere" guarantee `sdk.device.*` exists to provide. |
| Plugins call Capacitor plugins directly                               | Breaks plugin portability outright — a plugin written against Capacitor cannot run in a browser or PWA at all. Rejected outright, not seriously considered.                                                         |

## Recommendation (decided)

Capacitor as the shell framework; the device bridge is not designed in this
repo at all — it's a shared, versioned contract (`@sovereignfs/bridge` +
`@sovereignfs/sdk/device-client`) owned in the `sovereign` monorepo per RFC
0083, and this repo implements only its Capacitor transport.

See [ADR 0001](../adrs/0001-capacitor-as-shell-technology.md) and
[ADR 0004](../adrs/0004-shared-device-bridge-contract-with-desktop.md) for
the binding decision and its consequences.
