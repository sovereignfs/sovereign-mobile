---
status: Accepted
date: 2026-07-31
deciders: kasunben
---

# ADR 0002 — One universal binary, not a build per instance

## Status

Accepted. Locked by sovereign workstream 0002's "Decisions locked" table.

## Context

Sovereign is self-hosted: every user runs their own instance at their own
URL. A native app that hardcodes a backend, or that requires each
self-hoster to produce and sign their own store submission, does not scale
to the product's actual user base and adds a distribution burden with no
real benefit.

## Decision

Ship **one binary** to the App Store and Play Store. On first launch the
user enters their own instance URL; the shell validates and persists it.
Any Sovereign instance works with the same published app — nothing is
baked in at build time.

## Rejected alternatives

- **A build per instance.** Each self-hoster would need to fork, configure,
  sign, and submit their own copy — an unreasonable burden that contradicts
  self-hosting's whole premise of "run the software, not maintain a
  packaging pipeline."

## Consequences

- Never hardcode an instance URL anywhere outside tests.
- First-launch onboarding and multi-instance switching are core, load-bearing
  features, not optional polish — see [epics/shell.md](../epics/shell.md).
- Instance identity/validation (sovereign epic task 20.2, a separate leg
  living in the `sovereign` monorepo, not here) is a prerequisite for this
  shell to distinguish a real Sovereign instance from any server that
  happens to return `200 OK`.
