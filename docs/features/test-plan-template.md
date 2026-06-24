# Test Plan: {feature-name}

## Metadata
- Feature: F{nnn}-{feature-slug}
- Status: draft / approved / completed
- Contract: `docs/features/F{nnn}-{feature-slug}/contract.md`
- Trace Tag: `TASK-{feature-slug}`

## Test Scope
- 

## Reuse Regression Focus
- Which reused seams must stay backward compatible:
- Regression risks introduced by reusing existing behavior:
- Tests that prove reused paths still work:

## P0
| ID | Scenario | Verification |
|----|----------|--------------|
| T1 |  |  |

## P1
| ID | Scenario | Verification |
|----|----------|--------------|
| T2 |  |  |

## Traceability
- AC-01 ->
- AC-02 ->
- AC-03 ->

## Execution Notes
- Automated tests for this feature should include the trace tag `TASK-{feature-slug}`.
- New feature permission coverage must verify that `admin` receives the new authorization first.
- All SQL required for this feature and all feature test-data SQL should be stored under `docs/features/F{nnn}-{feature-slug}/sql/`.
- Backend:
- Frontend:
- E2E: execute with the `admin` account and archive the Playwright report under `docs/features/F{nnn}-{feature-slug}/reports/`.
