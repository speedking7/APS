---
description: 契约变更审批流程
---

> Codex workflow surface: Managed in this repository at ".codex/workflows/change-contract.md". Use `AGENTS.md` plus the current Codex toolchain as the execution authority. Treat legacy verbs like `Read`, `Agent`, `SendMessage`, and `EnterWorktree` as workflow instructions, not CLI-native slash commands.


<!-- CODEX:CANONICAL:START -->
> Canonical execution contract
> - `AGENTS.md` is the governing execution authority.
> - `.codex/` is the user-facing entry and workflow surface.
> - `project.md` provides subordinate engineering guidance and cannot override `AGENTS.md`.
> - `.omx/` is the runtime ledger for context, state, specs, plans, and evidence pointers.
> - `docs/features/*` and `docs/bugfix/*` remain the only formal delivery surfaces.
> - `tools/ai-scaffold` is the primary pass/fail authority; shell and PowerShell scripts are compatibility wrappers.
> - Marker blocks are generator-owned canonical sections, not user-maintained preserved patches.
> - `.omx/` runtime data for this workflow cannot replace plan approval, contracts, reports, QA evidence, or gate results.
<!-- CODEX:CANONICAL:END -->




<!-- CODEX:WORKFLOW:START -->
## OMX 运行时最小合同

- **Mandatory `.omx` artifacts**
  - 创建或更新 contract-change state（位于 `.omx/state/*` 或 session state）
- **Optional `.omx` artifacts**
  - context snapshot
  - clarification spec
  - impact-analysis evidence pointers
- **Recoverability**
  - contract-change state 缺失可重建
  - `.omx/*` 缺失不得阻塞 formal contract 变更文档落地
- **Formal authority**
  - 正式契约变更仍以目标 feature/bugfix 目录中的 `contract.md` 及相关正式文档为准
  - `.omx/*` 不能替代契约审批、版本递增、`verify-contract.sh` 或 formal contract 文档
<!-- CODEX:WORKFLOW:END -->

> Codex adaptation: Migrated from ".codex/workflows/change-contract.md". Use AGENTS.md plus the current Codex toolchain as the execution authority. Treat legacy verbs like Read, Agent, send_input, and CreateWorktree as workflow instructions, not CLI-native slash commands.


你是契约变更守卫 (contract-change-guard)。

**重要**：执行前必须先读取你的角色定义文件。

# 执行步骤

## Step 1: 读取角色定义
```
Read: .codex/agents/contract-change-guard.md
```

## Step 2: 按角色定义执行
严格按照 `.codex/agents/contract-change-guard.md` 中定义的职责、强制规则和流程执行。

## Step 3: 输出审批报告
按照 agent 定义中的报告模板输出审批报告。

---

# 快速参考（完整定义请阅读 agent 文件）

## 变更类型
| 类型 | 影响 | 审批要求 |
|------|------|----------|
| 新增字段 | 低 | 自动批准 |
| 新增接口 | 低 | 自动批准 |
| 修改字段 | 中 | 需要评估 |
| 删除字段 | 高 | 严格审批 |
| 修改响应结构 | 高 | 严格审批 |

## 强制规则
1. **向后兼容优先** - 优先选择兼容的变更方式
2. **影响最小化** - 选择影响面最小的方案
3. **必须通知** - 变更必须通知所有相关方
4. **版本递增** - 每次变更必须更新版本号

---

变更请求： $ARGUMENTS
