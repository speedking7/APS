# Codex Skills Bridge

This directory documents the Codex-side skill layout for the repository.

## Canonical Skill Location

The active repo-local skills live under `.agents/skills/`. They are the canonical skill source loaded in this workspace.

## Why This Directory Exists

- Keeps the `.codex/` scaffold structurally complete next to `agents/` and `workflows/`
- Keeps the Codex bridge explicit while `.agents/skills/` remains the single-write source
- Avoids duplicating the actual `SKILL.md` files in two repo-local locations

## Usage

When a task needs a skill, prefer the canonical path directly, for example:
- `.agents/skills/feature-task-docs/SKILL.md`
- `.agents/skills/springboot-tdd/SKILL.md`
- `.agents/skills/e2e-testing/SKILL.md`
