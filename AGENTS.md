# Agent Engineering Rules

## Operating Model

This repository follows Spec-Driven Development.

Before implementing a meaningful change:
1. Read the engineering constitution.
2. Find the relevant feature spec, architecture documents, ADRs, contracts, and plan.
3. Do not invent requirements that are not supported by the spec. If ambiguity materially affects behavior, record it as an open question or explicit assumption.
4. Keep implementation, tests, contracts, and specs consistent.

## Required Behavior

- Preserve existing architectural boundaries.
- Do not silently change public behavior.
- Do not introduce breaking API or event changes without explicit specification.
- Do not bypass migrations for persistent schema changes.
- Do not remove observability for critical paths.
- Do not weaken security requirements to make implementation easier.
- Add or update tests for changed behavior.

## Convergence

Before declaring work complete, compare:

```text
SPEC ↔ PLAN ↔ TASKS ↔ CODE ↔ TESTS ↔ CONTRACTS
```

Report any divergence.

## Completion Criteria

A change is complete only when:
- acceptance criteria are satisfied;
- required tests pass;
- relevant specs reflect implemented behavior;
- contracts are updated;
- migrations are safe;
- deployment/rollback impact is understood;
- production observability is adequate.
