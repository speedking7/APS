---
description: 功能开发前产出 plan.md（人审批准后再进入 /build-feature）
---

> Codex workflow surface: Managed in this repository at ".codex/workflows/plan-feature.md". Use `AGENTS.md` plus the current Codex toolchain as the execution authority. Treat legacy verbs like `Read`, `Agent`, `SendMessage`, and `EnterWorktree` as workflow instructions, not CLI-native slash commands.


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

- 本 workflow 的**强制第一步**是 `$deep-interview`
- `deep-interview` 完成后，**必须**基于其产出的 spec / interview 结论继续执行 `$ralplan`
- 只有在 `deep-interview` 与 `$ralplan` 都已完成后，才允许进入正式 `plan.md` 起草
- 典型运行时产物：
  - `.omx/context/*`
  - `.omx/interviews/*`
  - `.omx/specs/deep-interview-*.md`
  - `.omx/plans/prd-*.md`
  - `.omx/plans/test-spec-*.md`
- 按项目归档规则，必须同步把规划证据归档到对应 feature 目录：
  - `docs/features/F{nnn}-{slug}/reports/planning/deep-interview.md`
  - `docs/features/F{nnn}-{slug}/reports/planning/prd.md`
  - `docs/features/F{nnn}-{slug}/reports/planning/test-spec.md`
- 本 workflow 仍以 `docs/features/.../plan.md` 为唯一正式批准入口；OMX 技能负责澄清、规划和运行时账本，feature 目录负责正式归档与审阅证据

## OMX 运行时最小合同

- **Mandatory `.omx` artifacts**
  - 创建或复用 `.omx/context/<slug>-<timestamp>.md`
  - 创建或补齐 `.omx/interviews/*` 中与本 feature 对应的 interview 记录
  - 创建或更新 `.omx/specs/deep-interview-<slug>.md`
  - 创建或更新 `.omx/plans/prd-<slug>.md`
  - 创建或更新 `.omx/plans/test-spec-<slug>.md`
  - 更新对应 planning state（位于 `.omx/state/*` 或 session state）
- **Recoverability**
  - 若 `.omx/context/*` 或 planning state 缺失，可立即重建后继续
  - 若 `.omx/interviews/*` 缺失但 `deep-interview` 规格仍可重建，则先补齐 interview 记录再继续
  - 若 `.omx/specs/deep-interview-<slug>.md` 缺失，则阻塞 `plan.md` 正式产出
  - 若 `.omx/plans/prd-<slug>.md` 或 `.omx/plans/test-spec-<slug>.md` 缺失，则阻塞 `plan.md` 正式产出，并先重新执行 `$ralplan`
- **Formal authority**
  - 唯一正式产物仍是 `docs/features/F{nnn}-{slug}/plan.md`
  - 规划证据必须归档到 `docs/features/F{nnn}-{slug}/reports/planning/`
  - `.omx/*` 不能替代 `plan_status: approved` 或 `approved_at`
<!-- CODEX:WORKFLOW:END -->

> Codex adaptation: Migrated from ".codex/workflows/plan-feature.md". Use AGENTS.md plus the current Codex toolchain as the execution authority. Treat legacy verbs like Read, Agent, send_input, and CreateWorktree as workflow instructions, not CLI-native slash commands.


你是主编排角色（tech-lead-orchestrator），在**编写业务代码之前**完成「方案与范围」文档。先澄清范围，再把结论落到 `plan.md`；本 workflow 负责落盘与状态约定。

# 目标

1. 在 `docs/features/F{三位序号}-{feature-slug}/` 下创建或更新 **`plan.md`**。
2. 初始状态为 **`plan_status: draft`**；**只有**经人审改为 **`approved`** 并填写 **`approved_at`** 后，才允许执行 `/build-feature` 进入 Phase 1（见 `build-feature` 的 Phase 0.5 门禁）。
3. **禁止**在 `plan_status` 非 `approved` 时编写或合并该功能的业务实现代码（契约/测试计划可在批准后的 `build-feature` 中产出）。

# Phase 0 - 解析 feature 目录并准备归档位置

在执行 `deep-interview` 之前，必须先解析 feature 目录与 slug，用于后续 interview / ralplan 产物的正式归档。

要求：

1. 从 **`$ARGUMENTS`** 中解析或向用户确认：
   - **功能目录路径**：`docs/features/F{nnn}-{feature-slug}/`
   - **feature slug**：`{feature-slug}`
2. 若用户仅提供 **slug** 或 **自然语言描述**：
   - 读取 [`NEXT_FEATURE_NUMBER.txt`](../../docs/features/NEXT_FEATURE_NUMBER.txt)
   - 建议下一个编号
   - 明确最终目录名后再创建目录
3. 若 feature 目录不存在：
   - 创建 `docs/features/F{nnn}-{feature-slug}/`
   - 创建 `docs/features/F{nnn}-{feature-slug}/reports/planning/`
   - 按团队规范递增 [`NEXT_FEATURE_NUMBER.txt`](../../docs/features/NEXT_FEATURE_NUMBER.txt)
4. 若目录已存在：
   - 复用现有目录
   - 确保 `reports/planning/` 存在

若无法确定目录路径，**停止**并提示用户明确：`F{nnn}-{feature-slug}` 或完整相对路径。

# Phase 1 - 强制 deep-interview 澄清

在任何 `plan.md` 起草、更新、覆盖之前，必须先完成一次 `deep-interview` 边界澄清；这不是“按需建议”，而是本 workflow 的硬前置。

要求：

1. 针对该 feature 执行 `$deep-interview`，至少澄清：
   - Intent / Desired Outcome
   - In Scope / Out of Scope
   - Decision Boundaries
   - Exception Scenarios
   - 关键未决策点
2. 在 `.omx/interviews/*` 与 `.omx/specs/deep-interview-<feature-slug>.md` 留存正式澄清产物。
3. **立刻执行**以下命令归档 `deep-interview` 产物：
   ```bash
   node tools/ai-scaffold/dist/cli.js archive-planning-artifacts docs/features/F{nnn}-{feature-slug} --stage deep-interview
   ```
4. 将澄清产物归档到 `docs/features/F{nnn}-{feature-slug}/reports/planning/deep-interview.md`。
5. 若尚未完成 `deep-interview`，**禁止**直接写 `plan.md` 或进入 `$ralplan`。

硬停止条件：

- 若用户只说“直接起草计划”，也必须先完成 `deep-interview` 再继续。
- 若无法形成 `.omx/specs/deep-interview-<feature-slug>.md`，停止，不得进入 `$ralplan` 或 `plan.md` 起草。
- 若无法将 `deep-interview` 产物归档到 feature 目录，停止，不得进入 `$ralplan` 或 `plan.md` 起草。

# Phase 2 - 基于 deep-interview 产物强制执行 ralplan

`$ralplan` 不是可选建议，而是 `deep-interview` 之后的强制步骤。必须基于本 feature 的 interview/spec 结论生成可执行规划，再回写正式 `plan.md`。

要求：

1. 使用 `.omx/specs/deep-interview-<feature-slug>.md` 与对应 `.omx/interviews/*` 作为 `$ralplan` 输入，不得脱离 interview 结论另起炉灶。
2. 执行 `$ralplan` 后，必须生成：
   - `.omx/plans/prd-<feature-slug>.md`
   - `.omx/plans/test-spec-<feature-slug>.md`
3. **立刻执行**以下命令归档 `$ralplan` 产物：
   ```bash
   node tools/ai-scaffold/dist/cli.js archive-planning-artifacts docs/features/F{nnn}-{feature-slug} --stage ralplan
   ```
4. 将上述产物归档到：
   - `docs/features/F{nnn}-{feature-slug}/reports/planning/prd.md`
   - `docs/features/F{nnn}-{feature-slug}/reports/planning/test-spec.md`
5. `plan.md` 的范围、边界、方案、风险、验收思路，必须以该次 `$ralplan` 输出为直接依据。

硬停止条件：

- 若未执行 `$ralplan`，不得写 `plan.md`
- 若缺少 `.omx/plans/prd-<feature-slug>.md` 或 `.omx/plans/test-spec-<feature-slug>.md`，停止，不得写 `plan.md`
- 若无法将 `$ralplan` 产物归档到 feature 目录，停止，不得写 `plan.md`

# Phase 3 - 创建或更新 plan.md

1. 若 `plan.md` 已存在：在保留 frontmatter 的前提下更新正文（除非用户要求重置为 `draft`）。
2. 写入 **`plan.md`**，**必须**包含以下 **YAML frontmatter**（值在起草阶段如下）：

```yaml
---
plan_status: draft
approved_at: ""
---
```

3. 正文建议章节（可按功能裁剪）：

- 背景与目标
- 范围（In / Out）
- 技术方案要点（前后端、数据、接口草案）
- 风险与依赖
- 开放问题
- 与后续 `TASK.md` 中验收项（AC-xx）的对应关系（可先列草案）

补充要求：

- `plan.md` 的边界、非目标、异常场景，必须与前序 `deep-interview` 结论一致；若不一致，应先更新澄清结果再写正式计划。
- `plan.md` 的技术方案、风险、验证路径，必须与当前 feature 的 `reports/planning/prd.md` 与 `reports/planning/test-spec.md` 一致；若不一致，应先更新 `$ralplan` 产物再回写 `plan.md`
- `plan.md` 必须包含独立的 `Reuse Strategy` / `复用策略` 章节，至少写清：要复用的既有服务 / 组件 / DTO / SQL / 权限 / 测试基座、明确禁止复制的平行实现、以及必须新增抽象时为什么现有 seam 不可复用。
- `plan.md` 中应显式引用本次规划证据归档路径：
  - `reports/planning/deep-interview.md`
  - `reports/planning/prd.md`
  - `reports/planning/test-spec.md`

# Phase 4 - 人审说明（由人执行，非 Agent）

输出明确提示：

1. 审查人阅读 `plan.md` 正文。
2. 通过后，**人工**将 frontmatter 修改为：

```yaml
---
plan_status: approved
approved_at: YYYY-MM-DD
---
```

3. 本地校验（可选）：在仓库根执行  
   `node tools/ai-scaffold/dist/cli.js check-plan-approved docs/features/F{nnn}-{feature-slug}`  
   退出码 **0** 表示通过。

4. 然后再运行 **`/build-feature`**（须在参数或上下文中带上**同一功能目录**，以便 Phase 0.5 门禁）；在本仓库中，`/build-feature` 还会硬校验前序 **`$deep-interview`** 与 **`$ralplan`** 产物是否存在。

# 强制规则

1. **不得**在 `plan_status: approved` 之前宣称「可以开始开发」。
2. **`plan.md` 与 `TASK.md` 分离**：本阶段不写完整 `TASK.md`（除非用户明确要求合并编写）；`TASK.md` 在 `/build-feature` Phase 1 中从模板创建。
3. **所有计划起草之前必须先使用 `deep-interview` 澄清边界**；不得跳过、不得用“需求看起来已经清楚”替代此步骤。
4. **`deep-interview` 完成后必须继续执行 `$ralplan`**；不得把 `$ralplan` 视为可选步骤。
5. **`deep-interview` / `ralplan` 生成的文档必须按项目规则归档到对应 feature 目录的 `reports/planning/`**；不得只留在 `.omx/` 中。
6. **`plan.md` 缺少 `Reuse Strategy` / `复用策略` 章节时，不得进入批准状态。**
7. 若与全局 [project.md](../../project.md) 冲突，以**契约与防幻觉**为准。

用户需求： $ARGUMENTS
