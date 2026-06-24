---
description: Bug 修复工作流
---

> Codex workflow surface: Managed in this repository at ".codex/workflows/fix-bug.md". Use `AGENTS.md` plus the current Codex toolchain as the execution authority. Treat legacy verbs like `Read`, `Agent`, `SendMessage`, and `EnterWorktree` as workflow instructions, not CLI-native slash commands.


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
## OMX 技能参与方式

- 小 bug 可以直接走本 workflow
- 较复杂或边界不清的 bug，建议先走：
  - `$deep-interview --quick`：快速澄清症状、边界和不做项
  - `$ralplan`：当修复范围、契约影响或验证策略仍需共识时
- 若 bugfix 后续需要持续串行推进，可交给 `$ralph`
- 若 bugfix 涉及多条独立修复/验证线，可交给 `$team`
- 无论采用哪种 OMX 技能，正式 bugfix 产物仍必须落在 `docs/bugfix/*` 或相关 `docs/features/*`

## OMX 运行时最小合同

- **Mandatory `.omx` artifacts**
  - 创建 `.omx/context/<bug-slug>-<timestamp>.md`
  - 创建或更新 bugfix state（位于 `.omx/state/*` 或 session state）
- **Optional `.omx` artifacts**
  - 当需要额外澄清时，写入 `.omx/specs/*`
  - 写入验证证据指针和恢复元数据
- **Recoverability**
  - `.omx/context/*` 或 bugfix state 缺失可重建
  - `.omx/*` 缺失不得阻塞正式 bugfix 文档与修复落地
- **Formal authority**
  - 正式 bugfix 文档和报告仍应位于 `docs/bugfix/*` 或相关 `docs/features/*`
  - `.omx/*` 不能替代 bug 文档、契约校验、测试报告或 gate 结果
<!-- CODEX:WORKFLOW:END -->

> Codex adaptation: Migrated from ".codex/workflows/fix-bug.md". Use AGENTS.md plus the current Codex toolchain as the execution authority. Treat legacy verbs like Read, Agent, send_input, and CreateWorktree as workflow instructions, not CLI-native slash commands.


你是 Bug 修复工程师，严格按以下流程执行：

# Phase 1 - 问题定位

## 1.1 信息收集
1. 阅读问题描述，理解 Bug 表现
2. 确认复现步骤
3. 确认预期行为 vs 实际行为

## 1.2 问题分析
1. 搜索相关代码
2. 定位问题根源
3. 确认影响范围

## 1.3 关联功能目录（可选，不阻塞）
若修复与某 `docs/features/F*/` 相关，可 **Read** 该目录下的 `plan.md`：若存在且 `plan_status` 仍为 `draft`，提示复核人审状态；**不强制**等待批准，以免小修被流程卡死。

# Phase 2 - 解决方案

## 2.1 方案设计
1. 确定修复方案
2. 评估修复影响
3. 确认是否需要契约变更

## 2.2 契约变更（如需要）
如果修复涉及接口变更：
1. 读取现有契约文档
2. 评估变更影响
3. 更新契约文档（如批准）

# Phase 3 - 实现

## 3.1 后端修复
1. 编写复现 Bug 的测试用例（红灯）
2. 修复 Bug（绿灯）
3. 验证测试通过

## 3.2 前端修复（如需要）
1. 修复前端相关代码
2. 验证修复效果

# Phase 4 - 验证

## 4.1 单元测试
- 运行相关单元测试
- 确保修复没有破坏现有功能

## 4.2 集成测试
- 验证前后端集成
- 验证数据库操作

## 4.3 回归测试
- 检查相关功能是否正常
- 确保没有引入新问题

# Phase 5 - 完成

## 5.1 文档更新
1. 更新相关文档
2. 记录修复原因和方案

## 5.2 总结报告
输出 Bug 修复报告

# 强制规则
1. **先写测试** - 必须先编写复现 Bug 的测试
2. **最小修改** - 只修改必要的代码
3. **回归验证** - 必须验证没有引入新问题
4. **文档更新** - 重要修复必须更新文档

# Bug 修复报告模板
```markdown
# Bug Fix Report

## Bug Information
- ID: {bug-id}
- Title: {bug-title}
- Severity: Critical / Major / Minor

## Analysis
- Root Cause: {根本原因}
- Affected Files: {影响文件}
- Related Features: {相关功能}

## Solution
- Approach: {解决方案}
- Changes Made:
  - {文件1}: {变更说明}
  - {文件2}: {变更说明}

## Testing
- [ ] 复现测试已添加
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 回归测试通过

## Contract Change
- [ ] 不涉及契约变更
- [ ] 契约已更新: {契约文件}

## Verification
- [ ] Bug 已修复
- [ ] 无副作用
- [ ] 文档已更新
```

Bug 描述： $ARGUMENTS
