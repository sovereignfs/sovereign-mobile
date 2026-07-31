# AGENTS.md — sovereignfs/sovereign-mobile

Canonical, agent-agnostic guidance for this repository. `CLAUDE.md` points
here and carries no content of its own, so there is only one file to keep
true.

## What this is

**sovereign-mobile** — the native mobile shell for
[Sovereign](https://github.com/sovereignfs/sovereign), the modular,
self-hostable workspace runtime. A minimal Capacitor app: on first launch
the user enters their self-hosted instance URL; the shell validates it,
persists it, and loads it in the native WebView. Multiple instances are
supported. iOS and Android ship together from the same codebase.

This repo is a sibling of the `sovereign` monorepo, following the same
pattern as the already-shipped `sovereign-desktop` (Tauri). The
specification lives centrally: **sovereign RFC 0058**
(`docs/rfcs/0058-native-mobile-app-shell.md`), **sovereign RFC 0083**
(`docs/rfcs/0083-device-bridge-capability-contract.md`, the device bridge
protocol), **sovereign epic 20** (`docs/epics/mobile.md`), and **SRS §3.12**.
Read them, and this repo's own [CONCEPT.md](CONCEPT.md), before changing
this shell's scope or behavior.

**Distinct from `sovereign-edge`** — a separate, unrelated repository in
this workspace building a standalone, fully offline React Native AI app
with its own UI. Shares no code or architecture with this repo. Do not
conflate the two.

## Source of truth

Read the relevant document before implementing — these are authoritative
over assumptions:

- [CONCEPT.md](CONCEPT.md) — vision, architecture, device API strategy,
  phasing.
- [ROADMAP.md](ROADMAP.md) — chronological task index and **canonical task
  status**. One row per task.
- [docs/epics/](docs/epics/README.md) — full task detail by stable epic task
  ID (shared with `sovereign`'s epic 20): goal, deliverables, review
  checklist.
- [docs/adrs/](docs/adrs/README.md) — **binding** decisions: shell
  technology, distribution model, auth model, bridge sharing. Do not reopen
  an `Accepted` ADR while implementing a task — stop and say so instead.
- [docs/research/](docs/research/README.md) — open questions: findings,
  options considered, and a recommendation, before a decision exists.
- [docs/development-workflow.md](docs/development-workflow.md) — task
  lifecycle and how the documents above fit together.
- [CONTRIBUTING.md](CONTRIBUTING.md) — setup, branching, commits, PRs, CI.

**Write the ADR or research doc before building on a guess.** For a settled
architectural question, cite the existing ADR. For an open one with no
concrete design yet, write the research doc first.

## Hard rules — the minimal-shell philosophy

Carried over from `sovereign-desktop`'s equivalent rules, since this repo
follows the identical architectural pattern:

- **No product features in the shell.** Everything user-facing is served by
  the user's instance. If a feature could live in the instance, it must.
  This shell provides only: onboarding, instance persistence/switching,
  WebView lifecycle, and native glue the bridge genuinely requires.
- **TypeScript-first; native (Swift/Kotlin) code only for glue a Capacitor
  plugin doesn't already cover**, or that must survive the WebView
  navigating to remote content. Do not add native code where a Capacitor
  plugin API exists.
- **Never hardcode an instance URL** anywhere outside tests — see
  [ADR 0002](docs/adrs/0002-universal-one-binary-distribution.md). This
  shell is universal: one binary for every self-hosted instance.
- **Remote instance content must never get uncontrolled native access.**
  The device bridge (see below) is the **only** channel from page JS to
  native capability, and it is capability-negotiated and versioned — never
  a blanket bridge object exposing everything.
- **Instance validation targets the public liveness/identity endpoint**
  (`/api/health` today; the richer identity endpoint from sovereign task
  20.2 once it ships) — never `/api/admin/health`, which is admin-key-gated
  and would 403.
- **`server.url` → the remote instance over `https`. Never bundle assets
  behind `capacitor://`** — see
  [ADR 0005](docs/adrs/0005-server-url-not-bundled-assets.md); that scheme
  yields no service worker at all.

## Device bridge rules

From [ADR 0004](docs/adrs/0004-shared-device-bridge-contract-with-desktop.md)
and sovereign RFC 0083 — violating either of these breaks the contract for
`sovereign-desktop` too, not just this repo:

- **Expose only the narrow bridge object to page JS — never
  `window.Capacitor`.** The bridge protocol is designed once, centrally, and
  shared between this shell and `sovereign-desktop`; this repo implements
  its Capacitor transport, not a competing protocol.
- **The advertised `capabilities` list at handshake must reflect exactly
  what this build actually implements** — never aspirational, never stale
  after a capability is added or removed.
- **A new device capability is a `sovereign` monorepo change first**, to
  RFC 0083's registry, **then** a `sovereign-mobile` PR implementing the
  transport. Do not add a Capacitor plugin call reachable from page JS
  without a corresponding registry entry.

## Conventions

Carried over from the `sovereign` monorepo and `sovereign-desktop`:

- **Prettier** is the single source of style truth. Never add overrides.
- **ESLint 9 flat config.** Never disable rules inline without a comment
  explaining why. Prefix intentionally-unused identifiers with `_`.
- **Branch per change**, from up-to-date `main`: `feat/<slug>`, `fix/<slug>`,
  `docs/<slug>`, `chore/<slug>`.
- **Commits** end with the Claude Code attribution trailer (model-agnostic):
  `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- **PRs** target `main`, created as GitHub drafts first
  (`gh pr create --draft`); bodies end with
  `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Merge strategy: rebase and merge** — never squash, never merge commits.
- **Never merge a PR automatically.** Wait for explicit instruction.
- **Verify before claiming done** — run the checks in
  [CONTRIBUTING.md](CONTRIBUTING.md) and show the output, then actually
  exercise the behavior (see
  [docs/development-workflow.md](docs/development-workflow.md#verification)).

### Versioning

Repo semver follows the change type (`fix/` → patch, `feat/` → minor,
breaking → major). Keep `package.json` and native project version fields
(`Info.plist` `CFBundleShortVersionString`, Android `versionName`) in
lockstep — matching `sovereign-desktop`'s rule for its own three version
slots. Release tags are `vX.Y.Z`. This repo's own roadmap slot tracking
lives in [ROADMAP.md](ROADMAP.md) — a shared task also tracked in
`sovereign`'s epic 20 should stay traceable to that epic task ID (see
[docs/adrs/README.md](docs/adrs/README.md) on citing "sovereign RFC 0058",
never a bare "RFC 0058" — RFC numbering does not transfer between repos in
this ecosystem).

## Working conventions

- **One task at a time.** Implement a single task, verify its review
  checklist, then stop for human review. Do not start a task on an
  unmerged PR.
- **Tasks are sequenced** per [ROADMAP.md](ROADMAP.md)'s phases — don't
  skip ahead without saying so.
- **Cross-repo tasks wait on their `sovereign`-monorepo prerequisite's own
  PR queue**, not this repo's — see
  [docs/epics/README.md](docs/epics/README.md#owned-elsewhere-referenced-here).
- **When a task completes, mark it ✅ in both `ROADMAP.md` and the matching
  `docs/epics/<file>.md` heading, in the same PR.**
- **Docs are part of the change.** A change to the bridge capability
  registry usage, the instance-validation logic, or a documented command
  means updating the matching doc in the same PR.

## Relationship to the wider ecosystem

This repo owns its own docs, epics, ADRs, and research. The ecosystem
workbench (`sovereignfs/sovereignfs`) owns cross-repo concerns and the
public docs site; `sovereign` owns the workspace runtime, the device bridge
protocol, and the instance validation endpoint this shell depends on.
Conventions here are adapted from `sovereign-desktop`'s `CLAUDE.md` (the
architectural template) and `sovereign-edge`'s `AGENTS.md`/workflow docs
(the doc-scaffolding template, since this repo — like that one once was —
has no committed code yet).
