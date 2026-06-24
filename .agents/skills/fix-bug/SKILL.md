---
name: fix-bug
description: >-
  Execute the repository's standard bugfix workflow by reading
  `.codex/workflows/fix-bug.md`, creating or updating a dedicated
  `docs/bugfix/{bug-id}-{slug}/` directory, and driving the repair,
  verification, and report flow with repository gates intact. Use when the
  user asks for `/fix-bug` or wants a tracked bugfix delivered through the
  repo workflow.
---

# Fix Bug Workflow Skill

Use this skill when the user wants a bug fixed through the repository's formal
bugfix workflow.

## What this skill does

1. Read `AGENTS.md`, `project.md`, and `.codex/workflows/fix-bug.md`.
2. Determine the bugfix directory under `docs/bugfix/{bug-id}-{slug}/`.
3. Use quick clarification when needed:
   - `$deep-interview --quick` for vague symptoms
   - `$ralplan` when the fix scope or verification path still needs consensus
4. Produce the formal bugfix artifacts and then implement the repair.

## Required outputs

- `docs/bugfix/{bug-id}-{slug}/bug.md`
- `docs/bugfix/{bug-id}-{slug}/test-plan.md`
- `docs/bugfix/{bug-id}-{slug}/reports/*`
- `docs/bugfix/{bug-id}-{slug}/sql/*` when SQL is needed

## Hard rules

- Keep one bug per bugfix directory.
- Do not bypass validation, review, or QA evidence.
- Prefer evidence-backed root-cause fixes over speculative edits.

## Invocation

- Explicit: `$fix-bug <bug-id|bug description>`
- Equivalent repository command: `/fix-bug <bug-id|bug description>`

## Execution note

This skill is an execution surface for `.codex/workflows/fix-bug.md`.
When invoked, read that workflow and carry it out rather than improvising a new flow.
