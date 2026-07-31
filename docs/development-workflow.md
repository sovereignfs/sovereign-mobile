# Sovereign Mobile — Development Workflow

How tasks are planned, started, implemented, and closed out. Designed for
agentic execution with human oversight.

Adapted from `sovereign-edge`'s `docs/development-workflow.md`, which is
itself adapted from `sovereign`'s. The shape is kept the same across
ecosystem repos so moving between them doesn't mean relearning it; the parts
that presuppose a monorepo or an SRS are not carried over — see
[Not adopted](#not-adopted-yet).

---

## Four-layer information architecture

```
AGENTS.md / CLAUDE.md      ← conventions and hard rules; no task pointer
    │
    └─▶ ROADMAP.md         ← chronological index; one row per task; canonical status
            │
            └─▶ docs/epics/<domain>.md   ← full task detail: Goal, Deliverables,
            │                              Dependencies, Review checklist
            │
            └─▶ docs/adrs/ + docs/research/  ← why: locked decisions and open questions
```

This repo adds one layer `sovereign-edge` doesn't have: **ADRs**
(`docs/adrs/`), because several of this repo's foundational decisions
(shell technology, distribution model, auth model, bridge sharing) were
already locked centrally before this repo existed, and need a durable record
independent of the research process that produced them. See
[docs/adrs/README.md](adrs/README.md) for why, and how ADRs differ from
research docs here.

| File                     | Job                             | Read it for                                      |
| ------------------------ | ------------------------------- | ------------------------------------------------ |
| `AGENTS.md`              | Conventions and hard rules      | How to work here; what must never be violated    |
| `CLAUDE.md`              | Claude Code adapter             | Nothing — it points at `AGENTS.md`               |
| `ROADMAP.md`             | Version-ordered task index      | Which tasks exist, their status, which epic file |
| `docs/epics/<file>.md`   | Full task spec                  | Goal, deliverables, review checklist             |
| `docs/adrs/<n>-*.md`     | Decision record (binding)       | What was decided and its consequences            |
| `docs/research/<n>-*.md` | Decision record (open question) | Options considered before (or absent) a decision |

There is no "next task" pointer anywhere in this stack. **The developer
assigns the next task at session start.** A stored pointer goes stale the
moment priorities shift, and two agents reading it would collide.

---

## Epic structure

Work is organized into domain epics, indexed in
[docs/epics/README.md](epics/README.md): shell, bridge, release. Each task
carries the **stable epic task ID** it was assigned centrally in the
`sovereign` monorepo's epic 20 (e.g. `20.1`) — this repo does not renumber
tasks it shares with the monorepo.

### Stable IDs vs volatile slots

**Epic task IDs are permanent** and shared with `sovereign`. Use them in
commit subjects, PR titles, and cross-references.

**Roadmap slot versions are this repo's own and volatile.** A slot like
`0.1.2` reflects current sequencing here and shifts when priorities change.
Look the live slot up in `ROADMAP.md`; keep slots out of branch names and
commit subjects.

---

## Task lifecycle

### Starting a task

1. The developer names the task (epic task ID or description).
2. Confirm `main` is clean and up to date: `git switch main && git pull`.
3. Look up the epic file via [docs/epics/README.md](epics/README.md) and
   read the full task block — goal, deliverables, dependencies, review
   checklist.
4. Read any ADR or research doc the task references. **Do not re-decide an
   Accepted ADR** — if a task seems to require reopening one, stop and say
   so rather than quietly working around it.
5. If the task turns on a genuinely open question with no ADR yet, write the
   research doc first.
6. Cut the branch: `feat/<slug>`, `fix/<slug>`, `docs/<slug>`, or
   `chore/<slug>`.

### During implementation

- **One task at a time.** Do not start a task on an unmerged PR.
- Commit as you go, with messages that explain _why_.
- When something you assumed turns out to be wrong, say so in the same turn
  rather than quietly correcting course.
- **Cross-repo tasks** (several bridge/release tasks depend on work owned in
  the `sovereign` monorepo — see [docs/epics/README.md](epics/README.md#owned-elsewhere-referenced-here))
  may need to wait on that repo's own PR queue. The "previous leg's PR must
  be merged first" rule applies **per repository**, not across them — see
  sovereign workstream 0002's "Cross-repo parallelism" note for the
  precedent.

### Completing a task

1. **Verify.** Run the checks in [CONTRIBUTING.md](../CONTRIBUTING.md) —
   and then actually exercise the change (see Verification, below).
2. **Update status.** Mark the task ✅ in `ROADMAP.md` _and_ the matching
   `docs/epics/<file>.md` heading, in the same PR.
3. **Record decisions.** If the task settled an open question, promote the
   relevant research doc into a new ADR (or update an existing one) rather
   than leaving the decision only in a PR description.
4. **Open a draft PR** with `gh pr create --draft`. Mark it ready for review
   only on explicit instruction, and **never merge automatically.**

---

## Verification

Running checks is necessary, not sufficient. The review checklist in each
epic task describes an observable behavior — that is the thing to
demonstrate, on a real build, not just a passing test suite.

`sovereign-edge`'s history has two concrete examples of a fully green suite
hiding a real bug: a stall watchdog that reported the wrong error code
because a promise race only occurs against a real download, and a build step
(`expo prebuild`) that exits 0 when the platform-native step it depends on
actually failed. The applicable lesson here: **check the artefact, not the
exit code**, and for anything with a runtime surface, drive it on a real
simulator/emulator — or, where the task's review checklist says so, a
physical device (see [docs/epics/shell.md](epics/shell.md) Risks: simulator-only
verification is explicitly insufficient for the shell-loads-and-authenticates
check).

---

## Status tracking

Status lives in exactly two places:

| Location                     | Tracks                                       |
| ---------------------------- | -------------------------------------------- |
| `ROADMAP.md` row Status cell | ✅ / ⏳ / 📋 per task — the canonical record |
| Open PRs                     | Which tasks are currently in flight          |

Epic file headings (`#### ✅ X.Y — …`) are updated when a task completes.
Close a task by marking **both** the roadmap row and the epic heading in the
same PR. Do not accumulate completion history in `AGENTS.md` or `CLAUDE.md`.

---

## ADRs vs research docs

- **ADR** (`docs/adrs/`): a decision with binding consequences, `Accepted`
  by default here whenever it restates something already locked centrally
  (see [docs/adrs/README.md](adrs/README.md)), or moved through
  `Proposed` → `Accepted` when decided in this repo directly.
- **Research** (`docs/research/`): an open question, or the record of how a
  now-Accepted ADR was reached. Write one **before** building on an
  unverified assumption. "Not now" and "rejected" are valid outcomes.

When a research question resolves into a binding decision, write the ADR and
leave the research doc as-is (don't delete it) with a status line pointing
at the ADR — see [research 0001](research/0001-shell-and-bridge-technology-choice.md)
for the pattern.

---

## Not adopted (yet)

Machinery `sovereign`'s workflow includes that this repo does not need at
its current size — recorded so the divergence is deliberate:

| Not adopted                                 | Why                                                                                                                                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `CURRENT_TASK.md`                           | A transient task-scratch file written by `sovereign`-specific `/sv-*` skills that don't exist here                                          |
| Workstreams and legs (as a formal artifact) | This repo's own roadmap is one task at a time; cross-repo sequencing is tracked in `sovereign`'s workstream 0002 instead of duplicated here |
| SRS references                              | No SRS here. ADRs and epic files carry the requirement detail                                                                               |
| Per-package version bumps                   | Single app, single `package.json`. Version tracks the roadmap slot of the last completed task                                               |

Revisit these if this repo outgrows the simpler model, not before.

---

## Quick reference

| I need to know…                       | Read…                                                 |
| ------------------------------------- | ----------------------------------------------------- |
| What task is next                     | Ask the developer; `ROADMAP.md` lists what's pending  |
| Full spec for a task by epic ID       | `docs/epics/<file>.md` — grep for `^#### .*<id>`      |
| Which epic a task belongs to          | `docs/epics/README.md`                                |
| Why a decision was locked             | `docs/adrs/` — index at `docs/adrs/README.md`         |
| An open question with no decision yet | `docs/research/` — index at `docs/research/README.md` |
| Project conventions and hard rules    | `AGENTS.md`                                           |
| Setup, branching, commits, PRs, CI    | `CONTRIBUTING.md`                                     |
