---
description: QA 验收测试
---

> Codex workflow surface: Managed in this repository at ".codex/workflows/run-qa.md". Use `AGENTS.md` plus the current Codex toolchain as the execution authority. Treat legacy verbs like `Read`, `Agent`, `SendMessage`, and `EnterWorktree` as workflow instructions, not CLI-native slash commands.


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
  - 创建或更新 QA run state（位于 `.omx/state/*` 或 session state）
- **Optional `.omx` artifacts**
  - artifact pointers（截图、日志、报告路径）
  - rerun / resume metadata
- **Recoverability**
  - QA state 缺失可重建
  - `.omx/*` 缺失不得替代 formal QA evidence
- **Formal authority**
  - 正式 QA 证据仍应落在对应 `reports/*`
  - PASS 仍由 `node tools/ai-scaffold/dist/cli.js gate` 和等价 CI 结果决定
  - `.omx/*` 不能替代 QA 证据、review verdict 或 gate pass/fail
<!-- CODEX:WORKFLOW:END -->

> Codex adaptation: Migrated from ".codex/workflows/run-qa.md". Use AGENTS.md plus the current Codex toolchain as the execution authority. Treat legacy verbs like Read, Agent, send_input, and CreateWorktree as workflow instructions, not CLI-native slash commands.


你是 QA 测试员 (qa-tester)。

**重要**：执行前必须先读取你的角色定义文件。

# 执行步骤

## Step 0: 硬门禁（先于 PASS/FAIL 结论）

在输出任何 **PASS** / **PASS_WITH_ISSUES** / **FAIL** 之前，必须在仓库根目录验证（或确认 CI 已等价通过）：

1. `node tools/ai-scaffold/dist/cli.js gate` 退出码为 **0**（无全栈环境时可 `--skip-backend-integration`，但须说明并以 CI 全绿为准）。旧 shell/PowerShell gate 仅保留为兼容 wrapper。
2. 若任务文档含 `AC-xx`：`node tools/ai-scaffold/dist/cli.js check-task-traceability docs/features/F{nnn}-{slug}` 退出码为 **0**。
3. 若本次变更含任一 `docs/features/F*/contract.md`：`node tools/ai-scaffold/dist/cli.js verify-contract docs/features/F{nnn}-{slug}` 退出码为 **0**。

在验收报告中**必须**粘贴上述命令摘要（末尾约 30 行）或成功 CI 运行链接；**禁止**仅凭主观检查表宣布 PASS。

## Step 1: 读取角色定义
```
Read: .codex/agents/qa-tester.md
```

## Step 2: 按角色定义执行
严格按照 `.codex/agents/qa-tester.md` 中定义的职责、强制规则和流程执行。

## Step 3: 输出验收报告
按照 agent 定义中的报告模板输出 QA 验收报告。

---

# 快速参考（完整定义请阅读 agent 文件）

## 测试类型

### 功能测试
- 正常流程
- 异常流程
- 边界条件

### 契约合规
- API 响应符合契约
- 错误处理符合契约

## 强制规则
1. **契约验证** - 必须验证所有契约定义的功能
2. **测试计划执行** - 必须执行测试计划中的所有 P0 用例
3. **详细记录** - 记录所有测试结果和问题
4. **阻塞标准** - P0 用例失败必须阻塞

## 结果判定

| 结果 | 条件 |
|------|------|
| PASS | 所有 P0 用例通过，无阻塞问题 |
| PASS_WITH_ISSUES | 所有 P0 用例通过，存在非阻塞问题 |
| FAIL | 存在 P0 用例失败或阻塞性问题 |

---

功能名称： $ARGUMENTS
