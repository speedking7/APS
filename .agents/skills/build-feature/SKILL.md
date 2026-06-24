---
name: build-feature
description: >-
  Execute the repository's staged feature delivery workflow by reading
  `.codex/workflows/build-feature.md`, enforcing the hard planning gate, and
  then driving TASK, contract, test-plan, implementation, review, QA, and
  quality-gate completion for one feature directory. Use when the user asks for
  `/build-feature` or wants to implement an already planned feature.
---

# Build Feature Workflow Skill

Use this skill when the user wants to implement a planned feature through the
repository's standard delivery workflow.

## What this skill does

1. Read `AGENTS.md`, `project.md`, and `.codex/workflows/build-feature.md`.
2. Enforce the planning gate before any implementation work.
3. Run the staged delivery flow:
   - `TASK.md`
   - `contract.md`
   - `test-plan.md`
   - backend
   - frontend
   - integration review
   - code review
   - QA
   - quality gate

## Hard planning gate

Before Phase 1, run:

```bash
node tools/ai-scaffold/dist/cli.js check-build-feature-prereqs docs/features/F{nnn}-{slug}
```

That gate must pass all of the following:

- `plan.md` is human-approved
- `.omx/specs/deep-interview-{slug}.md` exists
- `.omx/plans/prd-{slug}.md` exists
- `.omx/plans/test-spec-{slug}.md` exists

If any prerequisite is missing, stop and direct the flow back to:

- `/plan-feature`
- `$deep-interview`
- `$ralplan`

## Required outputs

- `docs/features/F{nnn}-{slug}/TASK.md`
- `docs/features/F{nnn}-{slug}/contract.md`
- `docs/features/F{nnn}-{slug}/test-plan.md`
- `docs/features/F{nnn}-{slug}/reports/*`
- `docs/features/F{nnn}-{slug}/sql/*` when SQL is needed

## Hard rules

- Do not bypass the planning gate.
- Do not start frontend before backend is done when the workflow says backend-first.
- Use `node tools/ai-scaffold/dist/cli.js render-agent-prompt --role <role> ...` before spawning every child agent, and pass the rendered prompt. This is required because it injects both the full `.codex/agents/*` brief and each declared skill's canonical `SKILL.md` body.
- Re-run review and QA after fixes.
- Run the required quality gate before claiming completion.

## Invocation

- Explicit: `$build-feature <feature-dir|goal>`
- Equivalent repository command: `/build-feature <feature-dir|goal>`

## Execution note

This skill is an execution surface for `.codex/workflows/build-feature.md`.
When invoked, read that workflow and carry it out rather than improvising a new flow.
