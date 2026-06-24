<!-- CODEX:CANONICAL:START -->
> Canonical execution contract
> - `AGENTS.md` is the governing execution authority.
> - `.codex/` is the user-facing entry and workflow surface.
> - `project.md` provides subordinate engineering guidance and cannot override `AGENTS.md`.
> - `.omx/` is the runtime ledger for context, state, specs, plans, and evidence pointers.
> - `docs/features/*` and `docs/bugfix/*` remain the only formal delivery surfaces.
> - `tools/ai-scaffold` is the primary pass/fail authority; shell and PowerShell scripts are compatibility wrappers.
> - Marker blocks are generator-owned canonical sections, not user-maintained preserved patches.
<!-- CODEX:CANONICAL:END -->

# Codex AI Development Scaffold

This directory is the Codex-first AI development scaffold for the repository. It gives Codex users the repo-local entry surface while OMX handles runtime state and orchestration underneath it.

## Structure

```text
.codex/
|-- agents/        # Codex role briefs generated from `.agents/agents/`
|-- workflows/     # Repo-local workflow playbooks; current user-facing source
|-- skills/        # Bridge docs for the canonical repo-local skills that live under .agents/skills/
|-- agent-memory/  # Optional per-role memory location for Codex-oriented orchestration
|-- worktrees/     # Optional temporary worktrees referenced by workflow docs
|-- templates/     # Feature document templates reused by scripts and agents
|-- tasks/         # Optional working area for Codex session artifacts
`-- docker-config/ # Environment helpers
```

## Authority Stack

- `AGENTS.md` governs execution, orchestration, and verification.
- `.codex/` is the user-facing entry surface for workflows, role briefs, and operational notes.
- `project.md` provides subordinate engineering and project guidance.
- `.omx/` stores runtime context, state, specs, plans, resume data, and evidence pointers.
- `docs/features/*` and `docs/bugfix/*` remain the only formal delivery surfaces.
- Existing gate scripts remain the only pass/fail authority.
- Project-specific paths, commands, accounts, service/container names, E2E settings, and shell/PDA adapter artifacts belong in `ai-scaffold.config.json`, not in the reusable scaffold core.

## Source Mapping

- `.agents/agents/*` -> `.codex/agents/*`
- `.codex/workflows/*` -> current repo-local workflow source
- `.agents/skills/*` -> canonical runtime skills live under `.agents/skills/*`
- `docs/features/TASK-template.md` -> `.codex/templates/TASK.md`

## OMX Runtime Ledger

Use `.omx/` for:
- context snapshots
- interview/spec artifacts
- plan artifacts
- execution/review/qa state
- resume metadata
- evidence pointers

Do not treat `.omx/` as:
- a replacement for `docs/features/*` or `docs/bugfix/*`
- a replacement for `reports/*`
- a plan approval source
- a gate pass/fail source

## Workflow Invocation

Codex CLI may not register repo workflows like `/build-feature` as native slash commands. When the CLI says a slash command is unrecognized, invoke the workflow by asking Codex to read the matching file under `.codex/workflows/` and execute it.

Examples:
- `Read .codex/workflows/plan-feature.md and execute it for F123-my-feature`
- `Read .codex/workflows/build-feature.md and carry out the workflow for docs/features/F123-my-feature/`
- `Use .codex/workflows/fix-bug.md to resolve this bug: ...`

Each workflow may read or write `.omx/` runtime artifacts, but its formal outputs must still land in the matching `docs/features/*` or `docs/bugfix/*` directory.

## Skill Invocation

The same repo workflows are also available as repo-local skills:

- `$plan-feature`
- `$build-feature`
- `$fix-bug`

These skills are thin execution surfaces over the matching files in `.codex/workflows/` and the canonical instructions in `.agents/skills/`.

Examples:

- `$plan-feature supplier-portal-sso`
- `$plan-feature docs/features/F021-mock-sso-login-validation`
- `$build-feature docs/features/F021-mock-sso-login-validation`
- `$fix-bug BUG-142 login callback loops after refresh`
- `$fix-bug docs/bugfix/BUG-142-login-callback-loop`

Recommended usage:

1. Plan a new feature with `$plan-feature <slug|idea|feature-dir>`
2. Complete the required planning artifacts and approvals
3. Implement with `$build-feature <feature-dir>`
4. Handle tracked defects with `$fix-bug <bug-id|description|bugfix-dir>`

## Native Agent Mapping

Files under `.codex/agents/` are project role briefs, not native `spawn_agent` type names. Use them as injected instructions while routing the actual child agent to the closest native type:

| Project brief | Native `agent_type` |
| --- | --- |
| `contract-architect` | `architect` |
| `test-designer` | `test-engineer` |
| `backend-tdd-engineer` | `executor` |
| `frontend-engineer` | `executor` |
| `integration-checker` | `verifier` |
| `code-reviewer` | `code-reviewer` |
| `qa-tester` | `test-engineer` |
| `tech-lead-orchestrator` | leader/main thread |

Example: read `.codex/agents/backend-tdd-engineer.md`, then spawn `agent_type=executor` with that brief embedded in the task prompt.

## Recommended Start Points

- Migration guide: `docs/ai-scaffold-migration.md`
- Project adapter config: `ai-scaffold.config.json`
- Copy scaffold to another repo: `node tools/ai-scaffold/dist/cli.js copy-scaffold --target <target-repo>`
- New feature scaffold: `node tools/ai-scaffold/dist/cli.js init-feature --slug <slug> --title <title>`
- Sync scaffold: `node tools/ai-scaffold/dist/cli.js sync-codex`
- Quality gate: `node tools/ai-scaffold/dist/cli.js gate`
- Main rules: `AGENTS.md`
- Project guide: `project.md`

## Sync Ownership

`node tools/ai-scaffold/dist/cli.js sync-codex` regenerates `.codex/README.md`, `.codex/agents/*`, and bridge surfaces from repo-local sources. Workflow files remain repo-local source files under `.codex/workflows/*`; the sync command only normalizes their canonical metadata in place.

## Session Checklist

1. Read `AGENTS.md`
2. Read `project.md`
3. Read the relevant `.codex/agents/*.md` and `.codex/workflows/*.md`
4. Treat `project.md` as subordinate engineering guidance if it overlaps with `AGENTS.md`
5. Use the canonical repo-local skills under `.agents/skills/` when a workflow or task calls for a skill
6. Work inside an approved feature directory under `docs/features/` or a bugfix directory under `docs/bugfix/`
7. Run the platform-neutral scaffold CLI from `tools/ai-scaffold/`
8. Keep every test report under the active feature directory, including Playwright E2E output
9. Use the configured E2E account from `ai-scaffold.config.json`
10. Keep all executable SQL and test-data SQL under the active `sql/` directory
11. Use `copy-scaffold --dry-run` before copying this scaffold into a new repository
