# Codex Scaffold Migration

This note explains how the repository retired the older Claude-specific scaffold in favor of the current Codex-first layout.

For migrating the AI development scaffold into another repository, use [`docs/ai-scaffold-migration.md`](../docs/ai-scaffold-migration.md). That guide treats business-code paths, service names, database names, and login accounts as project adapters rather than scaffold core.

## Layers

- `.agents/agents/` stores the canonical shared role briefs
- `.codex/agents/` stores generated Codex-facing role briefs from `.agents/agents/`
- `.codex/workflows/` stores the current repo-local workflow surfaces
- `.agents/skills/` stores the canonical repo-local skill bodies
- `.codex/skills/` stores bridge files that point back to `.agents/skills/`

## Why Skills Are Not Duplicated

Duplicating every `SKILL.md` under both `.agents/skills/` and `.codex/skills/` would create drift. The bridge layout keeps one canonical copy while preserving a Codex-friendly directory shape.

## Native Agent Mapping

`.codex/agents/*.md` are project role briefs, not native `spawn_agent` type names.

Use this mapping when you actually launch a child agent:

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

## Regeneration

When `.agents/agents/`, `.agents/skills/`, or repo-local Codex docs change, regenerate the Codex scaffold surfaces with:

```powershell
powershell -File scripts/codex/sync-codex-scaffold.ps1
```

That script refreshes:

- `.codex/agents/`
- canonical metadata inside `.codex/workflows/`
- `.codex/skills/`
- `.codex/README.md`

## Workflow Reminder

Repo workflow files under `.codex/workflows/` are not automatically native CLI slash commands. If the CLI rejects `/build-feature`, ask Codex to read the workflow file and execute it instead.
