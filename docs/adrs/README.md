# ADR index

Architecture Decision Records for `sovereign-mobile` — accepted decisions
with binding consequences, kept even after implementation lands. Contrast
with [docs/research/](../research/README.md): research captures an open
question before a decision exists; an ADR records the decision itself.

This repo adopts the ADR status vocabulary from `sovereign-os` (the only
other repo in the ecosystem using ADRs), since `sovereign` itself has no ADR
stage — see
[cross-repo-conventions](../../../confluence/concepts/cross-repo-conventions.md)
for why documentation systems differ per repo and don't transfer.

## Status vocabulary

`Draft` → `Proposed` → `Accepted` → `Implemented`, or `Superseded` /
`Rejected` / `Archived` at any point after `Proposed`.

Most ADRs in this repo start life as `Accepted`, not `Draft` — they record
decisions already locked centrally in
[sovereign RFC 0058](https://github.com/sovereignfs/sovereign/blob/main/docs/rfcs/0058-native-mobile-app-shell.md)
and
[workstream 0002](https://github.com/sovereignfs/sovereign/blob/main/docs/workstreams/0002-native-mobile-app-release.md)'s
"Decisions locked" table before this repository existed. Citing "sovereign
RFC 0058" rather than a bare "RFC 0058" is deliberate — `sovereign-os` runs
an unrelated RFC numbering series, and the convention in this ecosystem is
to always name which repo a citation belongs to.

## Index

| ADR                                                        | Title                                                                  | Status   |
| ---------------------------------------------------------- | ---------------------------------------------------------------------- | -------- |
| [0001](0001-capacitor-as-shell-technology.md)              | Capacitor as the shell technology                                      | Accepted |
| [0002](0002-universal-one-binary-distribution.md)          | One universal binary, not a build per instance                         | Accepted |
| [0003](0003-cookie-in-webview-auth.md)                     | Cookie-in-WebView auth, not OAuth, for v1                              | Accepted |
| [0004](0004-shared-device-bridge-contract-with-desktop.md) | Device bridge is a shared, versioned contract with `sovereign-desktop` | Accepted |
| [0005](0005-server-url-not-bundled-assets.md)              | WebView loads `server.url`, never bundled `capacitor://` assets        | Accepted |
| [0006](0006-history-based-instance-switch-affordance.md)   | History-based instance-switch affordance, not native menu              | Accepted |
| [0007](0007-navigation-policy-enforcement.md)              | Navigation policy enforced by a thin native WebView delegate           | Accepted |

ADRs 0001–0005 restate decisions already locked centrally before this repo
had code. ADRs 0006–0007 are this repo's own — decided directly during task
20.1's implementation, since neither has a `sovereign-desktop` precedent
that transfers as-is.

Add a row here whenever a new ADR file is added.
