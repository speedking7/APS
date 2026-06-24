---
name: fix-bug
description: 'Execute the repository''s standard bugfix workflow by reading `.codex/workflows/fix-bug.md`, creating or updating a dedicated `docs/bugfix/{bug-id}-{slug}/` directory, and driving the repair, verification, and report flow with repository gates intact. Use when the user asks for `/fix-bug` or wants a tracked bugfix delivered through the repo workflow.'
canonical: .agents/skills/fix-bug/SKILL.md
---

# Codex Skill Bridge

This bridge keeps `.codex/skills/` aligned with the canonical `.agents/skills/` source without duplicating the real skill body.

- Canonical skill: `.agents/skills/fix-bug/SKILL.md`
- Description: Execute the repository's standard bugfix workflow by reading `.codex/workflows/fix-bug.md`, creating or updating a dedicated `docs/bugfix/{bug-id}-{slug}/` directory, and driving the repair, verification, and report flow with repository gates intact. Use when the user asks for `/fix-bug` or wants a tracked bugfix delivered through the repo workflow.

Use the canonical `.agents/skills/.../SKILL.md` file when the workflow needs the actual skill instructions.
