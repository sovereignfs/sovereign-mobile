# Research 0002 — Device capability candidates beyond RFC 0083's v1 slice

**Status:** Open. No candidate below is scoped or committed to any release.

## Question

Sovereign RFC 0083 deliberately ships a thin v1 bridge slice
(`haptics.impact`, `notifications.native`) to prove the capability-negotiation
contract, not to cover the device surface. What capabilities should be added
next, in what order, and what does each one actually require beyond "add a
registry entry"?

This doc exists so capability requests (from product, from plugin authors,
or from a specific plugin's needs) have one place to accumulate before they
turn into epic tasks — rather than each being decided ad hoc the moment
someone asks for one.

## Candidates identified so far

| Capability                             | Web API fallback exists?      | Native-only reason                                              | Notes                                                                                                                                                                         |
| -------------------------------------- | ----------------------------- | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Geolocation                            | Yes (`navigator.geolocation`) | Background location and higher accuracy need native APIs        | Web tier likely covers most plugin needs already; native tier only justified for background use — see sovereign research 0005 (trip-planning plugin) for a concrete consumer. |
| Camera / photo capture                 | Partial (`getUserMedia`)      | Native photo picker UX, existing-photo-library access           | Tracked centrally as sovereign epic task 20.6.                                                                                                                                |
| Calendar read/write                    | No                            | No Web API for OS calendar integration                          | Not currently referenced by any specific plugin need in this workspace; candidate only, not requested by a consumer yet.                                                      |
| NFC                                    | No                            | No Web API equivalent; iOS NFC access is especially restrictive | Candidate only; no consumer identified yet. Requires investigating iOS Core NFC entitlement and session-lifetime constraints before scoping.                                  |
| Biometric auth (Face ID / fingerprint) | No                            | Local device unlock gate, not a Sovereign auth mechanism        | Tracked centrally as sovereign epic task 20.7. Does not replace or duplicate instance-side auth — see [ADR 0003](../adrs/0003-cookie-in-webview-auth.md).                     |
| Background location / background work  | No                            | Requires background execution entitlements on both platforms    | Explicitly called out in RFC 0058 as needing its own capability RFC — highest-scrutiny candidate on this list.                                                                |
| Push notifications (APNs/FCM)          | Partial (Web Push)            | Reliability and delivery-when-app-closed guarantees             | Tracked centrally as sovereign epic task 20.5; deferred out of the first store release per workstream 0002.                                                                   |

## How a candidate graduates off this list

1. A concrete plugin need is identified (not speculative — an actual plugin
   blocked without it).
2. The capability is added to sovereign RFC 0083's registry (protocol
   change happens in `sovereign`, not here — see
   [ADR 0004](../adrs/0004-shared-device-bridge-contract-with-desktop.md)).
3. Permission string, privacy declaration, and consent UX are defined before
   any implementation starts.
4. An epic task is created in [docs/epics/bridge.md](../epics/bridge.md) and
   this row is updated to point at it.

## Open questions

- Should calendar and NFC be scoped now, speculatively, or left off the
  registry until a specific plugin asks for them? Current lean: leave them
  off — RFC 0083's stated strategy is additive-as-needed, not
  front-loaded.
- Does background location need a dedicated RFC before it's even a
  candidate here, given RFC 0058 already flags it as needing one?
