# Research 0003 — WKWebView offline behavior and service-worker constraints

**Status:** Pending spike. Tracked centrally as sovereign epic task 20.10 and
shared with sovereign workstream 0001; run once, both workstreams consume
the finding. This doc is the place that finding gets recorded from this
repo's side once the spike lands.

## Question

Once [ADR 0005](../adrs/0005-server-url-not-bundled-assets.md) commits this
shell to loading `server.url` over `https` (required for a service worker to
exist at all), how much of Sovereign's PWA offline behavior actually survives
inside a Capacitor WKWebView in practice? Assumption is not evidence here —
this has to be measured against a real Capacitor build, not inferred from
how service workers behave in Safari or Chrome directly.

## Why this matters here specifically

- Determines whether offline is a **feature of the first native release** or
  a **documented follow-up** — a scope decision, not a go/no-go one. This
  leg always produces a usable answer either way.
- iOS is known to evict `WKWebsiteDataStore` under storage pressure or
  prolonged non-use. For this whole-instance shell the visible effect of
  eviction is a forced re-login and a cold cache — annoying, not silent data
  loss, since the instance (not the shell) is the source of truth. Still
  needs to be measured, not assumed.
- A negative result narrows this repo's Phase 1 scope; it does not block the
  release. See [epics/shell.md](../epics/shell.md) kill criteria.

## What the spike needs to answer

1. Does a service worker registered by the loaded instance actually install
   and activate inside a Capacitor WKWebView pointed at `server.url`?
2. What survives a forced offline toggle mid-session — cached shell chrome,
   cached data, neither?
3. How does `WKWebsiteDataStore` eviction actually behave under realistic
   storage pressure and idle duration on a physical device (not a
   simulator)?
4. Does anything differ meaningfully between iOS and Android here, given
   Android's WebView has different service-worker and storage-eviction
   behavior?

## Recommendation

Not yet decided — pending the spike. Do not build Phase 1 offline-dependent
UX ahead of this finding; see
[epics/shell.md](../epics/shell.md) leg 1 for sequencing.
