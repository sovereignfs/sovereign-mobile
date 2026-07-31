# Contributing to Sovereign Mobile

Conventions here follow
[`sovereign`'s](https://github.com/sovereignfs/sovereign) and
[`sovereign-desktop`'s](https://github.com/sovereignfs/sovereign-desktop)
`CONTRIBUTING.md`/`CLAUDE.md` so that moving between ecosystem repos does
not mean relearning the rules. Where this repo differs, it's because it's a
Capacitor mobile shell rather than a monorepo web runtime or a Tauri desktop
app — those differences are called out rather than left implicit.

Agent-facing guidance lives in [AGENTS.md](AGENTS.md); the task lifecycle is
in [docs/development-workflow.md](docs/development-workflow.md).

> **This repo does not have committed code yet.** Task
> [20.1](docs/epics/shell.md#-201--capacitor-shell-scaffold) creates the
> Capacitor scaffold, `package.json` scripts, and CI workflows this document
> describes. Commands below are the intended shape (matching
> `sovereign-desktop`'s tooling, adapted for Capacitor), not yet runnable
> against this checkout — update this section in the same PR that lands the
> scaffold if the actual shape differs.

## Contents

- [Development setup](#development-setup)
- [Running the app](#running-the-app)
- [Running the tests](#running-the-tests)
- [Branching and commits](#branching-and-commits)
- [Pull requests](#pull-requests)
- [Continuous integration](#continuous-integration)
- [Proposing a change (ADRs and research docs)](#proposing-a-change-adrs-and-research-docs)

---

## Development setup

Requirements:

- **Node 24.x** and **pnpm 11** (matching the rest of the `sovereignfs`
  ecosystem).
- **iOS:** macOS with a current Xcode and CocoaPods (the native project also
  resolves Capacitor's Swift packages via SPM — no separate setup needed
  beyond Xcode itself).
- **Android:** **JDK 21+** (Capacitor Android 8's Gradle build fails to
  compile on JDK 17 with `invalid source release: 21` — confirmed against
  this repo's actual `:capacitor-android` module, not a guess) and the
  Android SDK, plus Android Studio for the emulator.
- **Capacitor CLI** (`@capacitor/cli`, installed as a dev dependency —
  no separate global install required).

```bash
git clone https://github.com/sovereignfs/sovereign-mobile.git
cd sovereign-mobile
pnpm install
```

### Testing against a local instance

Run the platform dev server in the `sovereign` monorepo (`pnpm dev`, port 3000) and add `http://localhost:3000` as an instance during onboarding.
`http://` is accepted when typed explicitly (LAN/dev instances); bare input
defaults to `https://` — matching `sovereign-desktop`'s validation rule.

## Running the app

```bash
pnpm dev:ios       # sync + open the iOS project in Xcode, run on Simulator
pnpm dev:android   # sync + open the Android project in Android Studio / run on Emulator
pnpm build         # production builds for both platforms
```

**`ios/` and `android/` are committed**, unlike `sovereign-edge`'s Expo
setup — Capacitor's native projects are the source of truth for
platform-specific config (permissions, entitlements, `Info.plist`,
`AndroidManifest.xml`), not regenerated from a single config file. Hand
edits to native project files are expected and preserved; run
`npx cap sync` after any change to the web assets or Capacitor plugin list
to propagate it into both native projects.

**Real-device verification is required before any release-track task is
called done** — see [docs/epics/shell.md](docs/epics/shell.md) Risks.
Simulator/emulator-only verification is insufficient for anything touching
instance load-and-authenticate behavior.

## Running the tests

```bash
pnpm test           # unit tests for shell logic (onboarding, storage, validation)
pnpm typecheck       # tsc --noEmit
pnpm lint            # eslint
pnpm format:check    # prettier --check
```

### Green tests are not proof

Exercise the actual behavior, not just the exit code — see
[docs/development-workflow.md](docs/development-workflow.md#verification)
for the pattern and two concrete precedents from `sovereign-edge`'s history
where a green suite hid a real bug.

## Branching and commits

Always branch from an up-to-date `main`:

```bash
git switch main && git pull
git switch -c feat/your-feature-name
```

**Branch prefixes:**

| Prefix   | Use for                                         |
| -------- | ----------------------------------------------- |
| `feat/`  | New features or capabilities                    |
| `fix/`   | Bug fixes                                       |
| `docs/`  | Documentation only                              |
| `chore/` | Tooling, scaffolding, dependencies, maintenance |

**Commit messages** should explain _why_, not just _what_. Keep the subject
under 72 characters; wrap body lines at 100.

**Epic task IDs** (e.g. `20.1`, `20.4`) are stable, shared with the
`sovereign` monorepo, and may be cited in commit subjects and PR titles.
**Roadmap slot versions** (e.g. `0.1.1`) are this repo's own and volatile —
do not use them in branch names or commit subjects.

If an AI assistant helped write the code, include the co-author trailer:

```
Co-Authored-By: Claude Code <noreply@anthropic.com>
```

## Pull requests

- **One logical change per PR.** Keep scope tight.
- All checks must pass before review: `pnpm format:check`, `pnpm lint`,
  `pnpm typecheck`, `pnpm test`.
- Cite the relevant ADR or research doc when a change implements or
  revisits a recorded decision.
- **Mark the task ✅ in both `ROADMAP.md` and the matching
  `docs/epics/<file>.md` heading, in the same PR.** Those are the only two
  places status is tracked.
- If a PR touches a task also referenced from the `sovereign` monorepo's
  epic 20 or workstream 0002, note the epic task ID in the PR description
  so the two repos stay traceable to each other.
- PRs are merged with **rebase and merge** — no squash, no merge commits,
  matching `sovereign-desktop`'s convention.
- **Fix commit messages before the PR is merged.** Correcting them
  afterwards means rewriting `main`.
- Agent-created PRs are opened as **drafts** (`gh pr create --draft`) and
  marked ready for review only on explicit instruction. **Never merge
  automatically.**
- PR bodies from Claude Code end with:
  `🤖 Generated with [Claude Code](https://claude.com/claude-code)`

## Continuous integration

Expected shape (finalized alongside task 20.1's scaffold, matching
`sovereign-desktop`'s split):

| Workflow      | Runs on                     | Does                                                                     |
| ------------- | --------------------------- | ------------------------------------------------------------------------ |
| `ci.yml`      | Every PR and push to `main` | format:check, lint, typecheck, test                                      |
| `release.yml` | `v*` tags                   | Signed iOS/Android release artifacts, attached to a draft GitHub Release |

Neither workflow should require model weights, live credentials for a real
Sovereign instance, or any network dependency beyond what a standard build
needs — this shell has no backend of its own to spin up in CI.

## Proposing a change (ADRs and research docs)

This repo uses two decision-record types, described fully in
[docs/development-workflow.md](docs/development-workflow.md#adrs-vs-research-docs):

- **ADRs** (`docs/adrs/`) record decisions with binding consequences —
  several already `Accepted` here because they were locked centrally in
  `sovereign` RFC 0058/0083/workstream 0002 before this repo existed.
- **Research docs** (`docs/research/`) capture an open question before a
  decision exists — findings, options considered, a recommendation.

Write a research doc **before** building on an unverified assumption; write
or update an ADR once a question resolves into a binding decision. Do not
reopen an `Accepted` ADR in the course of implementing a task — if a task
seems to require it, stop and say so.

## Licence

By contributing you agree that your contributions are licensed under
[AGPL-3.0-or-later](LICENSE), matching the wider `sovereignfs` ecosystem.
