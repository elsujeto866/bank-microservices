# 1. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-06

## Context

Every non-trivial decision in this project has an alternative that is also
defensible. Six months from now — or in a technical interview — the code shows
*what* was chosen but never *why*, and never what was rejected. That missing
context is what turns a reasonable decision into "legacy nobody dares touch".

## Decision

Every architecturally significant decision is recorded as a numbered Architecture
Decision Record in `docs/adr/`, following Michael Nygard's format: Context,
Decision, Consequences.

A decision is architecturally significant when it is expensive to reverse, or
when it constrains work that comes after it.

ADRs are immutable. A decision that no longer holds is not edited: a new ADR is
written and the old one is marked `Superseded by ADR-NNNN`. The trail of
reasoning is the artifact, not the conclusion.

## Consequences

- Onboarding reads the reasoning instead of reverse-engineering it.
- Discussions that were already settled do not get relitigated.
- Writing the ADR is itself a check: a decision that cannot be justified in
  prose is usually not ready to be committed to code.
- Cost: discipline. An ADR written after the fact is rationalisation, not a
  record.
