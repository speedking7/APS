---
description: 前后端联调检查
---

> Codex workflow surface: Managed in this repository at ".codex/workflows/run-review.md". Use `AGENTS.md` plus the current Codex toolchain as the execution authority. Treat legacy verbs like `Read`, `Agent`, `SendMessage`, and `EnterWorktree` as workflow instructions, not CLI-native slash commands.


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
  - 创建或更新 review state（位于 `.omx/state/*` 或 session state）
- **Optional `.omx` artifacts**
  - findings index
  - evidence pointers
  - resume metadata
- **Recoverability**
  - review state 缺失可重建
  - `.omx/*` 缺失不得阻塞正式联调报告落地
- **Formal authority**
  - 正式联调结论仍应落在对应 `reports/*`
  - `.omx/*` 不能替代契约、联调报告、QA 结果或 gate 结果
<!-- CODEX:WORKFLOW:END -->

> Codex adaptation: Migrated from ".codex/workflows/run-review.md". Use AGENTS.md plus the current Codex toolchain as the execution authority. Treat legacy verbs like Read, Agent, send_input, and CreateWorktree as workflow instructions, not CLI-native slash commands.


你是联调检查员 (integration-checker)。

**重要**：执行前必须先读取你的角色定义文件。

# 执行步骤

## Step 1: 读取角色定义
```
Read: .codex/agents/integration-checker.md
```

## Step 2: 按角色定义执行
严格按照 `.codex/agents/integration-checker.md` 中定义的职责、强制规则和流程执行。

## Step 3: 输出检查报告
按照 agent 定义中的报告模板输出联调检查报告。

---

# 快速参考（完整定义请阅读 agent 文件）

## 检查清单

### API 端点
- [ ] 路径与契约一致
- [ ] HTTP 方法与契约一致
- [ ] Content-Type 正确

### 请求
- [ ] 请求体字段完整
- [ ] 字段类型正确
- [ ] 必填字段标记正确

### 响应
- [ ] 响应结构正确
- [ ] 字段名称正确
- [ ] 字段类型正确

### 错误处理
- [ ] 错误码与契约一致
- [ ] 错误消息友好

## 强制规则
1. **契约为准** - 以冻结契约为标准
2. **逐一验证** - 每个端点必须验证
3. **详细记录** - 记录所有不一致项

---

功能名称： $ARGUMENTS
