# AI Development Scaffold Agent Guide

This repository contains a portable AI development scaffold. Replace this file's project-specific section after installing the scaffold in a real codebase.

## Operating Contract

- Use `.codex/` as the Codex-facing workflow surface.
- Use `.agents/agents/` and `.agents/skills/` as the canonical role and skill sources.
- Use `docs/features/F{nnn}-{slug}/` for feature plans, tasks, contracts, test plans, SQL, and reports.
- Use `docs/bugfix/{bug-id}-{slug}/` for tracked bugfix artifacts.
- Use `tools/ai-scaffold` as the pass/fail authority for scaffold checks.
- Keep project-specific paths, commands, accounts, and service names in `ai-scaffold.config.json`.
- Do not copy runtime state such as `.omx/`, `.codex/tasks/tmp/`, or `.codex/worktrees/*` into a target repo.
- For quick reuse, copy this bundle with `node copy-scaffold.mjs --target <target-repo>` or `node tools/ai-scaffold/dist/cli.js copy-scaffold --target <target-repo>` after building the CLI.
- Do not copy `tools/ai-scaffold/node_modules/`, `tools/ai-scaffold/dist/`, sample `docs/features/Fxxx-*`, `_temp/`, or business documents unless intentionally creating an offline/onboarding bundle.

## Default Workflow

1. Plan with `.codex/workflows/plan-feature.md`.
2. Build with `.codex/workflows/build-feature.md` after plan approval.
3. Review and QA with `.codex/workflows/run-review.md` and `.codex/workflows/run-qa.md`.
4. Verify with `node tools/ai-scaffold/dist/cli.js gate` or the target repo's equivalent CI.

## Target Repo Setup

After copying this scaffold, update:

- `project.md`
- `ai-scaffold.config.json`
- `.github/workflows/*` if CI is used
- any role brief that names target-specific paths or frameworks
- `ai-scaffold.config.json` adapter sections for runtime services (`backend`, `frontends`, `database`, `e2e`, `e2eDocker`, `manualTest`, `pdaShell`)


## Project Runtime Notes

- MySQL is accessed through WSL. When agents need to inspect or operate the local MySQL database, use WSL commands directly, for example `wsl mysql ...` or `wsl bash -lc "mysql ..."`, instead of assuming a native Windows MySQL client.
- Prefer keeping MySQL commands non-interactive and reproducible, e.g. `wsl bash -lc "mysql -u <user> -p<password> -h 127.0.0.1 -e '<SQL>'"`; use the repository configuration or existing environment variables for actual credentials when available.
