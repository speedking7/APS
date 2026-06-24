---
description: 自动执行任务流水线 - 读取计划逐个开发
---

> Codex workflow surface: Managed in this repository at ".codex/workflows/run-pipeline.md". Use `AGENTS.md` plus the current Codex toolchain as the execution authority. Treat legacy verbs like `Read`, `Agent`, `SendMessage`, and `EnterWorktree` as workflow instructions, not CLI-native slash commands.


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

- 本 workflow 是 repo-local pipeline 表面；实际执行姿态可按任务形态映射到 OMX 模式：
  - `$ralph`：一个任务接一个任务顺序推进
  - `$team`：在依赖允许时拆成多 lane 并行推进
- 当流水线中的任务描述本身仍然模糊时，应先回到 `$deep-interview` 或 `$ralplan`，而不是直接硬跑
- 本 workflow 负责串起 formal task list 与 `.omx/state/*` 运行时账本，不替代 `docs/IMPLEMENTATION-PLAN.md` 或正式 gate

## OMX 运行时最小合同

- **Mandatory `.omx` artifacts**
  - 创建或更新 pipeline state（位于 `.omx/state/*` 或 session state）
- **Optional `.omx` artifacts**
  - orchestration resume metadata
  - evidence pointers
  - context references to specs / plans when present
- **Recoverability**
  - pipeline state 缺失可重建
  - `.omx/*` 缺失不得替代 formal artifacts 或 gate 校验
- **Formal authority**
  - 本流程默认不创建新的 formal artifact
  - 只负责编排和验证现有 formal artifacts 与 gate
  - `.omx/*` 不能替代任务完成状态、formal reports 或 gate pass/fail
<!-- CODEX:WORKFLOW:END -->

> Codex adaptation: Migrated from ".codex/workflows/run-pipeline.md". Use AGENTS.md plus the current Codex toolchain as the execution authority. Treat legacy verbs like Read, Agent, send_input, and CreateWorktree as workflow instructions, not CLI-native slash commands.


你是流水线编排器（Pipeline Orchestrator），负责自动执行 IMPLEMENTATION-PLAN 中的任务。

# 强制规则
1. **必须先读取任务计划** - `docs/IMPLEMENTATION-PLAN.md`
2. **必须按依赖顺序执行** - 检查任务依赖是否满足
3. **一个任务完成后再执行下一个** - 不能并行执行多个 build-feature
4. **完成后更新计划文件** - 标记任务状态

# 工作流程

## Phase 1: 扫描任务状态

1. **读取任务计划**:
```
Read: docs/IMPLEMENTATION-PLAN.md
```

2. **解析任务状态**:
   - 🟢 可开始 - 无依赖阻塞
   - 🟡 阻塞中 - 依赖未完成
   - 🔵 进行中 - 正在开发
   - ✅ 已完成 - 开发完成

3. **识别下一个任务**:
   - 优先级排序: P0 > P1 > P2
   - 选择第一个 🟢 可开始的任务
   - 如果没有可开始任务，报告阻塞原因

## Phase 2: 执行任务

对于选中的任务:

### 2.1 显示任务信息
```
## 开始执行任务

**任务ID**: T1.x
**任务名称**: xxx
**优先级**: P0
**依赖**: T1.y (✅ 已完成)

**子任务**:
- [ ] 子任务1
- [ ] 子任务2
```

### 2.2 更新任务状态为进行中
修改 `docs/IMPLEMENTATION-PLAN.md`:
```
### T1.x 任务名称 (P0) 🔵  # 改为 🔵 进行中
```

### 2.3 调用 build-feature
使用 Skill 工具调用 `/build-feature`:
```
Skill: skill="build-feature", args="实现xxx功能：\n- 子任务1\n- 子任务2\n- ..."
```

### 2.4 等待完成
- 监控 build-feature 执行进度
- 等待任务完成通知

## Phase 3: 更新计划

任务完成后:

### 3.1 更新任务状态
修改 `docs/IMPLEMENTATION-PLAN.md`:
```
### T1.x 任务名称 (P0) ✅  # 改为 ✅ 已完成
```

### 3.2 更新子任务勾选
```
- [x] 子任务1
- [x] 子任务2
```

### 3.3 更新依赖任务状态
检查被当前任务阻塞的其他任务，如果依赖已满足，将其状态改为 🟢

## Phase 4: 循环执行

1. **检查是否继续**:
   - 读取更新后的 IMPLEMENTATION-PLAN.md
   - 查找下一个 🟢 可开始的任务

2. **决策**:
   - 如果有可开始任务 → 继续执行 Phase 2
   - 如果全部完成 → 输出完成报告
   - 如果全部阻塞 → 输出阻塞报告

# 输入参数: $ARGUMENTS

- **空** - 自动选择下一个可开始任务
- **任务ID** (如 T1.2) - 执行指定任务
- **--all** - 尝试执行所有可开始任务（一个接一个）
- **--status** - 只显示当前状态，不执行

# 输出格式

## 流水线状态报告

```markdown
# Pipeline Status Report

## 执行摘要
- **已执行**: X 个任务
- **当前**: 任务名称 (进行中)
- **剩余**: Y 个任务

## 任务列表

| ID | 任务 | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T1.1 | 多租户基础设施 | P0 | ✅ | - |
| T1.2 | 用户认证体系 | P0 | 🔵 | T1.1 ✅ |
| T1.3 | RBAC权限模型 | P0 | 🟡 | T1.2 |
| T1.5 | 租户管理 | P0 | 🟢 | T1.1 ✅ |

## 下一步
- 即将执行: T1.5 租户管理
- 预计阻塞: T1.3 (等待 T1.2 完成)

## 依赖关系图
\`\`\`
T1.1 ✅ ─┬─→ T1.2 🔵 ─→ T1.3 🟡 ─→ T1.4 🟡
         │
         └─→ T1.5 🟢 ─→ T1.6 🟡
\`\`\`
```

# 错误处理

1. **任务执行失败**:
   - 记录失败原因
   - 标记任务为 🔴 失败
   - 询问用户是否继续

2. **依赖循环**:
   - 检测并报告循环依赖
   - 停止执行

3. **无法解析计划**:
   - 报告格式错误
   - 请求手动修复

# 完成条件

1. 所有任务都已完成 (✅)
2. 或者所有未完成任务都被阻塞 (🟡)
3. 或者用户请求停止

---

用户参数: $ARGUMENTS
