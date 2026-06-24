---
name: build-feature
description: 'Execute the repository''s staged feature delivery workflow by reading `.codex/workflows/build-feature.md`, enforcing the hard planning gate, and then driving TASK, contract, test-plan, implementation, review, QA, and quality-gate completion for one feature directory. Use when the user asks for `/build-feature` or wants to implement an already planned feature.'
canonical: .agents/skills/build-feature/SKILL.md
---

# Codex Skill Bridge

This bridge keeps `.codex/skills/` aligned with the canonical `.agents/skills/` source without duplicating the real skill body.

- Canonical skill: `.agents/skills/build-feature/SKILL.md`
- Description: Execute the repository's staged feature delivery workflow by reading `.codex/workflows/build-feature.md`, enforcing the hard planning gate, and then driving TASK, contract, test-plan, implementation, review, QA, and quality-gate completion for one feature directory. Use when the user asks for `/build-feature` or wants to implement an already planned feature.

Use the canonical `.agents/skills/.../SKILL.md` file when the workflow needs the actual skill instructions.
