---
name: blueprint
description: 'Turn a one-line objective into a step-by-step construction plan for multi-session, multi-agent engineering projects. Each step has a self-contained context brief so a fresh agent can execute it cold. Includes adversarial review gate, dependency graph, parallel step detection, anti-pattern catalog, and plan mutation protocol. TRIGGER when: user requests a plan, blueprint, or roadmap for a complex multi-PR task, or describes work that needs multiple sessions. DO NOT TRIGGER when: task is completable in a single PR or fewer than 3 tool calls, or user says "just do it". origin: community'
canonical: .agents/skills/blueprint/SKILL.md
---

# Codex Skill Bridge

This bridge keeps `.codex/skills/` aligned with the canonical `.agents/skills/` source without duplicating the real skill body.

- Canonical skill: `.agents/skills/blueprint/SKILL.md`
- Description: Turn a one-line objective into a step-by-step construction plan for multi-session, multi-agent engineering projects. Each step has a self-contained context brief so a fresh agent can execute it cold. Includes adversarial review gate, dependency graph, parallel step detection, anti-pattern catalog, and plan mutation protocol. TRIGGER when: user requests a plan, blueprint, or roadmap for a complex multi-PR task, or describes work that needs multiple sessions. DO NOT TRIGGER when: task is completable in a single PR or fewer than 3 tool calls, or user says "just do it". origin: community

Use the canonical `.agents/skills/.../SKILL.md` file when the workflow needs the actual skill instructions.
