---
description: 使用 child-agent 串行流程完成功能交付
---

> Codex workflow surface: Managed in this repository at ".codex/workflows/build-feature.md". Use `AGENTS.md` plus the current Codex toolchain as the execution authority. Treat legacy verbs like `Read`, `Agent`, `SendMessage`, and `EnterWorktree` as workflow instructions, not CLI-native slash commands.


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

- 本 workflow 通常消费前序 `$deep-interview` / `$ralplan` 产物，而不是重新发明需求边界
- 当 `plan.md` 已批准、formal docs 已齐备后：
  - 单 owner 顺序推进可映射到 `$ralph`
  - 多 lane 并行推进可映射到 `$team`
- 典型读取产物：
  - `.omx/specs/deep-interview-*.md`
  - `.omx/plans/prd-*.md`
  - `.omx/plans/test-spec-*.md`
- 本 workflow 的正式交付面仍是 `docs/features/...`；`$ralph` / `$team` 只改变执行姿态，不改变 formal docs 和 gate 规则

## OMX 运行时最小合同

- **Mandatory `.omx` artifacts**
  - 创建或更新 execution state（位于 `.omx/state/*` 或 session state）
  - 读取已批准的 `plan.md` 作为 formal prerequisite
- **Optional `.omx` reads**
  - `.omx/specs/*`
  - `.omx/plans/*`
  - 相关 context snapshot
- **Optional `.omx` writes**
  - 阶段进度
  - 验证证据指针
  - resume metadata
- **Recoverability**
  - execution state 丢失可重建，不阻塞实现继续
  - `.omx/specs/*` / `.omx/plans/*` 缺失可继续，只要 formal docs 完整
  - approved `plan.md` 缺失或未批准必须停止
- **Formal authority**
  - `TASK.md`、`contract.md`、`test-plan.md`、`reports/*` 仍是唯一正式产物
  - `.omx/*` 不能替代 plan approval、contract freeze、review verdict、QA evidence 或 gate pass/fail
<!-- CODEX:WORKFLOW:END -->

> Codex adaptation: Migrated from ".codex/workflows/build-feature.md". Use AGENTS.md plus the current Codex toolchain as the execution authority. Treat legacy verbs like Read, Agent, send_input, and CreateWorktree as workflow instructions, not CLI-native slash commands.


你是主编排角色（tech-lead-orchestrator），严格按以下流程执行：

# Phase 0 - 初始化执行上下文

## 第一步：进入沙盒
在 `.codex/worktrees/feature-{feature-name}` 创建隔离 worktree（可用 `git worktree add` 或等效方式）：
```
推荐命令:
git worktree add .codex/worktrees/feature-{feature-name} HEAD
```

## 第二步: 读取并准备 child-agent 定义
**重要**：必须先读取 **根目录 `project.md`**、**agent 定义文件** 与该 agent 声明的 **skills 正文**，再按 **A + B0 + B + S + C** 组装 `prompt`（禁止仅用一句话代替 agent 全文，见「强制规则」第 14 条）。

**Skill 注入强制规则**：所有 child agent 的 prompt 必须通过脚手架命令渲染，不能手工只复制 `.codex/agents/<role>.md`。该命令会读取 agent frontmatter 的 `skills: [...]`，解析并加载每个技能的 canonical `SKILL.md` 正文，然后把这些 skill instructions 注入 prompt：

```bash
node tools/ai-scaffold/dist/cli.js render-agent-prompt \
  --role backend-tdd-engineer \
  --feature-dir docs/features/F{nnn}-{feature-slug} \
  --worktree .codex/worktrees/feature-{feature-name} \
  --task "本阶段任务说明" \
  --output .omx/state/rendered-prompts/backend-tdd-engineer.md \
  --summary
```

Spawn 时必须使用该命令生成的 prompt 文件内容。`--summary` 输出必须显示实际加载的 skills 和来源路径；若任一 skill body 缺失，命令失败，禁止继续 Spawn。

**Prompt 结构（所有 Spawn 的 child agent 均适用，Phase 5–7 的联调/审查/QA 同理）**

| 片段 | 内容 |
|------|------|
| **（A）** | 一两句角色身份（如「你是后端 TDD 工程师」） |
| **（B0）** | 根目录 **`project.md` 全文**或执行前 **Read `project.md`** 的显式指令：其内容只作为**从属于 `AGENTS.md` 的工程/项目指导**；若与 `AGENTS.md` 冲突，**必须以 `AGENTS.md` 为准**；若上下文过长可只注入 §1 与 §4 摘要，并**必须**要求 child agent 自行 Read 完整 `project.md` |
| **（B）** | **`Read` 得到的 `.codex/agents/<role>.md` 全文**（YAML frontmatter 可保留或去除，但正文规则须完整） |
| **（S）** | 从该 agent frontmatter `skills: [...]` 解析并读取的所有 canonical `SKILL.md` 正文 |
| **（C）** | 本阶段任务描述、路径与输入：`docs/features/F{nnn}-{slug}/`、`contract.md`、`TASK.md`、send_input 中的具体要求 |

**拼接顺序**：`prompt = （A）+ "\n\n" + （B0）+ "\n\n" + （B）+ "\n\n" + （S）+ "\n\n" + （C）`

```
# 1. 先用脚手架渲染带 skills 正文的 prompt
node tools/ai-scaffold/dist/cli.js render-agent-prompt --role contract-architect --feature-dir docs/features/F{nnn}-{feature-slug} --worktree .codex/worktrees/feature-{feature-name} --task "{契约阶段任务}" --output .omx/state/rendered-prompts/contract-architect.md --summary
node tools/ai-scaffold/dist/cli.js render-agent-prompt --role test-designer --feature-dir docs/features/F{nnn}-{feature-slug} --worktree .codex/worktrees/feature-{feature-name} --task "{测试计划阶段任务}" --output .omx/state/rendered-prompts/test-designer.md --summary
node tools/ai-scaffold/dist/cli.js render-agent-prompt --role backend-tdd-engineer --feature-dir docs/features/F{nnn}-{feature-slug} --worktree .codex/worktrees/feature-{feature-name} --task "{后端阶段任务}" --output .omx/state/rendered-prompts/backend-tdd-engineer.md --summary
node tools/ai-scaffold/dist/cli.js render-agent-prompt --role frontend-engineer --feature-dir docs/features/F{nnn}-{feature-slug} --worktree .codex/worktrees/feature-{feature-name} --task "{前端阶段任务}" --output .omx/state/rendered-prompts/frontend-engineer.md --summary

# 2. Spawn 时传递渲染后的完整 prompt（以下为示意）
spawn_agent(agent_type=architect, name=architect,
       prompt="{Read .omx/state/rendered-prompts/contract-architect.md 得到的完整内容}"

spawn_agent(agent_type=test-engineer, name=tester,
       prompt="{Read .omx/state/rendered-prompts/test-designer.md 得到的完整内容}"

spawn_agent(agent_type=executor, name=backend,
       prompt="{Read .omx/state/rendered-prompts/backend-tdd-engineer.md 得到的完整内容}"

spawn_agent(agent_type=executor, name=frontend,
       prompt="{Read .omx/state/rendered-prompts/frontend-engineer.md 得到的完整内容}"
```

**示例代码**:
```javascript
await run("node tools/ai-scaffold/dist/cli.js render-agent-prompt --role contract-architect --feature-dir docs/features/F123-demo --worktree .codex/worktrees/feature-demo --task \"冻结契约\" --output .omx/state/rendered-prompts/contract-architect.md --summary");
const architectPrompt = await Read(".omx/state/rendered-prompts/contract-architect.md");

spawn_agent({
  agent_type: "architect",
  name: "architect",
  prompt: architectPrompt
});
```

# Phase 0.5 - Planning 门禁（必须）

在 **Phase 1 之前**执行；未通过则 **停止整个流程**，不得创建 `TASK.md`、不得进入契约与开发阶段。

1. **解析功能目录** `docs/features/F{nnn}-{feature-slug}/`：
   - 从 **`$ARGUMENTS`** 中提取路径（例如 `docs/features/F004-inventory-export` 或 `F004-inventory-export`）；
   - 若参数中仅有功能描述，**必须**向用户确认准确的 **`F{nnn}-{feature-slug}`** 目录名（应与 `/plan-feature` 产出目录一致）。
   - 若仍无法确定目录，**停止**并提示：先运行 **`/plan-feature`** 创建 `plan.md`，或在命令参数中显式给出功能目录路径。
2. **执行校验**（在仓库根目录）：
   ```bash
   node tools/ai-scaffold/dist/cli.js check-build-feature-prereqs docs/features/F{nnn}-{feature-slug}
   ```
   该脚本必须同时校验：
   - `plan.md` 已人审批准；
   - `.omx/specs/deep-interview-{feature-slug}.md` 存在；
   - `.omx/plans/prd-{feature-slug}.md` 存在；
   - `.omx/plans/test-spec-{feature-slug}.md` 存在。
3. **若失败**（退出码非 0）：禁止进入 Phase 1；输出明确说明：请先完成 **`/plan-feature`**、**`$deep-interview`** 与 **`$ralplan`**，并确保 `plan.md` 中 **`plan_status`** 为 **`approved`** 且 **`approved_at`** 已填写，再重新执行 `/build-feature`。

# Phase 1 - 需求分析
1. 读取 `docs/features/TASK-template.md`，确定目录 **`docs/features/F{nnn}-{feature-slug}/`**（须与 Phase 0.5 一致）。若该目录**已由 `/plan-feature` 创建**且已含 `plan.md`，则**复用**该目录与编号，**不要**再次递增 `NEXT_FEATURE_NUMBER.txt`；若**本阶段**才首次创建目录，则从 `docs/features/NEXT_FEATURE_NUMBER.txt` 取号、建目录后递增该文件中的数字。
2. 在同目录创建 **`TASK.md`**（并保留 `TASK-template.md` 不修改）
3. 在 `TASK.md` 中补齐独立 `Reuse Plan` / `复用方案` 章节，明确要复用的现有服务 / 组件 / DTO / SQL / 权限 / 测试基座，以及不复用时的原因。
4. 总结需求、识别范围/风险
5. 输出验收标准草案

# Phase 2 - 契约冻结
1. send_input 通知 architect 开始
2. 等待完成，产出 **`docs/features/F{nnn}-{feature-slug}/contract.md`**
3. 验证契约状态为 frozen

# Phase 3 - 测试计划
1. send_input 通知 tester 开始
2. 等待完成，产出 **`docs/features/F{nnn}-{feature-slug}/test-plan.md`**（流程类报告建议放在同目录 `reports/`）

# Phase 4 - 串行开发（后端 -> 前端）
1. 确认契约冻结与测试计划已完成
2. 先 send_input 通知 backend 开始（必须执行 TDD）
3. 等待 backend 完成并通过其阶段检查
4. backend 完成前，禁止启动 frontend
5. backend 完成后，再 send_input 通知 frontend 开始
6. frontend 完成并通过其阶段检查后，进入下一阶段

## 反馈闭环（强制）
1. 任何阶段收到 FAIL / CHANGES_REQUIRED / 阻塞问题，主编排角色 必须判定责任归属并回派给对应 child agent：
   - 后端实现/接口/数据问题 -> backend-tdd-engineer
   - 前端页面/交互/适配问题 -> frontend-engineer
   - 契约问题 -> contract-architect
   - 测试计划缺陷 -> test-designer
   - 联调问题 -> 按根因回 backend 或 frontend（必要时同时回派）
   - **Code Review Phase 6 提出的问题**：先按根因回 backend/frontend（或 architect）修复；修复完成后 **必须再次 Spawn code-reviewer** 做完整复审，**更新** `docs/features/F{nnn}-{slug}/reports/code-review-report.md`（含新的 Verdict）。禁止仅由主编排角色 或开发 agent **手改** Verdict 冒充通过。
2. 主编排角色 不得跳过回派、不得自行宣布“已修复”。
3. 每次回派修复后必须 **重新触发对应阶段检查**：联调 -> 再 spawn integration-checker；代码审查 -> **再 spawn code-reviewer**；QA -> 再 spawn qa-tester；直到该阶段结论为放行（或脚本/门禁通过）。
4. 未完成“回派修复 -> 对应阶段复审/复验通过”闭环，不得进入下一阶段。

# Phase 5 - 联调检查
1. **先读取** `project.md` 与 `.codex/agents/integration-checker.md`
2. Spawn 一个 `verifier` child agent（注入 `integration-checker` brief），`prompt` 必须由 `render-agent-prompt --role integration-checker` 生成，结构为 **A+B0+B+S+C**（完整 `integration-checker.md` 为 B，声明 skills 的 canonical `SKILL.md` 为 S）
3. 检查前后端一致性
4. 未通过则返回修复

```
node tools/ai-scaffold/dist/cli.js render-agent-prompt --role integration-checker --feature-dir docs/features/F{nnn}-{feature-slug} --worktree .codex/worktrees/feature-{feature-name} --task "{联调检查任务}" --output .omx/state/rendered-prompts/integration-checker.md --summary

spawn_agent(agent_type=verifier,
       prompt="{Read .omx/state/rendered-prompts/integration-checker.md 得到的完整内容}"
```

# Phase 6 - 代码审查
1. **先读取** `project.md` 与 `.codex/agents/code-reviewer.md`
2. Spawn 一个 `code-reviewer` child agent，`prompt` 必须由 `render-agent-prompt --role code-reviewer` 生成，结构为 **A+B0+B+S+C**
3. 若报告 **Verdict** 为 **CHANGES_REQUIRED**：回派责任 child agent 修复后，**必须再次 Spawn code-reviewer**（新一轮完整审查），更新 `reports/code-review-report.md`；重复直到 Verdict 为放行（如 `PASS`、`PASS_WITH_COMMENTS` 等），**不得**仅改代码不重新 review、不得手改 Verdict。
4. 仅当最新一次 code-reviewer 产出之 Verdict 已放行时，才允许进入 Phase 7。

```
node tools/ai-scaffold/dist/cli.js render-agent-prompt --role code-reviewer --feature-dir docs/features/F{nnn}-{feature-slug} --worktree .codex/worktrees/feature-{feature-name} --task "{代码审查任务}" --output .omx/state/rendered-prompts/code-reviewer.md --summary

spawn_agent(agent_type=code-reviewer,
       prompt="{Read .omx/state/rendered-prompts/code-reviewer.md 得到的完整内容}"
```

# Phase 7 - QA 验收
1. **先读取** `project.md` 与 `.codex/agents/qa-tester.md`
2. Spawn 一个 `test-engineer` child agent（注入 `qa-tester` brief），`prompt` 必须由 `render-agent-prompt --role qa-tester` 生成，结构为 **A+B0+B+S+C**
3. 若 FAIL 则修复并重新 QA
4. PASS 才能完成

```
node tools/ai-scaffold/dist/cli.js render-agent-prompt --role qa-tester --feature-dir docs/features/F{nnn}-{feature-slug} --worktree .codex/worktrees/feature-{feature-name} --task "{QA 验收任务}" --output .omx/state/rendered-prompts/qa-tester.md --summary

spawn_agent(agent_type=test-engineer,
       prompt="{Read .omx/state/rendered-prompts/qa-tester.md 得到的完整内容}"
```

# Phase 7.5 - 硬门禁与完成定义（DoD）

在标记任何阶段为「完成」或进入 **Phase 8** 之前，**必须**满足以下可验证条件（禁止仅凭 Task 列表勾选或口头 PASS）：

1. **本地或 CI 命令成功**：在仓库根目录执行 `node tools/ai-scaffold/dist/cli.js gate`，**退出码为 0**（与 `.github/workflows/ci.yml` 一致：`mvn verify`、前端 `npm run lint`、`npm run test:ci`、`npm run build`）。**若本次变更涉及 `frontend/`**，必须再执行 `node tools/ai-scaffold/dist/cli.js gate --run-e2e`（会跑 Playwright，与 CI **E2E job** 对齐），或合并前以 **CI 全绿且含 E2E job** 为准。若本地无 PostgreSQL/Redis，可设 `--skip-backend-integration` 做降级检查，但合并前须以 CI 全绿为准。若在合并前需再次确认 **Plan 已批准** 且 **Code Review 已放行**，可执行 `node tools/ai-scaffold/dist/cli.js gate --feature-dir docs/features/F{nnn}-{slug}`，其中会先运行 plan / contract / traceability / code review verdict 等检查。仅本地调试可设 `--skip-code-review-verdict`，合并前仍须以含 Verdict 检查的全量门禁为准。旧 gate wrapper 仅保留为兼容层。
   - `--feature-dir` 门禁还必须校验 `plan.md` / `TASK.md` 已写入复用策略；缺少复用证据时必须失败，而不是靠 reviewer 自行补脑。
2. **验收 ID 可追溯**：若 `docs/features/F{nnn}-{slug}/TASK.md` 含 `AC-xx`，执行 `node tools/ai-scaffold/dist/cli.js check-task-traceability docs/features/F{nnn}-{slug}`，**退出码为 0**。
3. **契约变更时**：若本次变更触及任一 **`docs/features/F*/contract.md`**，执行 `node tools/ai-scaffold/dist/cli.js verify-contract docs/features/F{nnn}-{slug}`，**退出码为 0**。
4. **完成报告证据**：在 Phase 8 最终报告中，**必须**附上上述命令末尾日志摘要 **或** 对应 CI 运行成功链接；未附证据不得宣布交付完成。
5. **浏览器 E2E（Playwright）**：**凡变更配置中任一 frontend 路径下的代码**，必须在对应 frontend 的 `e2e/` 下新增或更新用例以覆盖本次行为，并确保 **CI 中 E2E job** 通过；本地合并前须执行 `node tools/ai-scaffold/dist/cli.js gate --run-e2e`，或进入对应 frontend 目录执行其 E2E 命令。主流程/新路由类改动仍须保证关键路径被 E2E 覆盖。

# Phase 8 - 完成总结与收尾

## ⚠️ 重要：必须按顺序完成所有清理步骤

### 8.1 合并代码到主分支
```
# 1. 在工作树中提交所有变更
cd .codex/worktrees/feature-{name}
git add -A
git commit -m "feat({name}): 实现功能描述

## 后端
- API 端点列表

## 前端
- 页面组件列表

## 测试
- 测试通过情况

# 2. 回到主仓库
cd {repo-root}

# 3. 合并工作树变更到主分支
git add -A
git commit -m "feat({name}): 实现功能描述

```

### 8.2 结束 child-agent 会话
```
# 确认所有已回派问题均已完成闭环（修复+复验）
# 记录各阶段最终结论与证据链接/日志摘要
```

### 8.3 清理资源
```
# 移除工作树
git worktree remove .codex/worktrees/feature-{name}
```

### 8.4 输出最终报告
```markdown
# Feature Completion Report

## 交付物
- Feature 目录: `docs/features/F{nnn}-{slug}/`（含 `plan.md`、`TASK.md`、`contract.md`、`test-plan.md`、`reports/`）
- 后端代码: backend/...
- 前端代码: frontend/...

## API 端点
| Method | Path | Description |
|--------|------|-------------|

## 验收状态
- [x] 所有 P0 测试通过
- [x] 代码审查通过
- [x] QA 验收通过

## Git 信息
- 分支: master
- 提交: {commit-hash}
```

# 强制规则
1. **必须先进入沙盒** - 不允许跳过 Phase 0 的 worktree 创建步骤
2. **必须使用 child-agent 串行编排** - 不允许并行启动 backend/frontend；必须后端完成后再前端
3. **未通过 Phase 0.5 Plan 门禁** - 不得进入 Phase 1（需求分析）及后续契约/开发阶段；`plan.md` 须为 **`plan_status: approved`** 且 **`approved_at`** 已填
4. 契约未冻结前不允许开发
5. 后端必须使用 TDD
6. Review 未通过不得进入 QA
7. QA 未通过不得宣布完成
8. **硬门禁未通过不得宣布完成** - Phase 7.5 中 `node tools/ai-scaffold/dist/cli.js gate` / `check-task-traceability` / `verify-contract`（若适用）必须成功，且最终报告含命令或 CI 证据
9. **任何反馈问题必须回派对应 child agent 修复并复验**；其中 **Code Review 发现问题** 在修复后 **必须再次 Spawn code-reviewer** 复审并更新报告，主编排角色 不得跳过复审、不得手改 Verdict 放行
10. **完成后必须清理** - 必须按 8.1 → 8.2 → 8.3 → 8.4 顺序执行
11. **代码必须合并到主分支** - 不允许代码留在工作树分支
12. 每个阶段必须输出: 当前阶段 / 已完成内容 / 阻塞点 / 下一步
13. **凡含配置中 frontend 路径下的代码变更**须同步对应 frontend 的 `e2e/` 并通过 **E2E (Playwright)**（见 Phase 7.5 第 1、5 条与 `RUN_E2E=1`）
14. **Spawn child agent 时禁止仅用一句话概括角色**而不传入 **`.codex/agents/<role>.md` 全文**；必须通过 `node tools/ai-scaffold/dist/cli.js render-agent-prompt` 注入 **B0（`project.md`）**、**B（agent 全文）** 与 **S（agent frontmatter skills 对应的 canonical `SKILL.md` 正文）**，并保留 `--summary` 证据；违者视为未执行 `/build-feature`
15. **禁止绕过质量门禁** - 不得指示或默许跳过 `node tools/ai-scaffold/dist/cli.js gate`、CI、Git/Husky 钩子；Phase 8 合并与任意宣称「完成」前，门禁须真实通过（见 Phase 7.5）
16. **禁止 `--no-verify`** - 工作树内提交、合并说明或教用户执行时，**不得**使用 `git commit --no-verify` / `git push --no-verify` 绕过验证
17. **Human-in-the-loop** - 同一门禁失败经**三轮**有记录的修复仍无法解决时，**停止**自动重试循环，向用户输出阻塞摘要与日志要点，请求人工介入后再继续；禁止无限重试同一错误模式

用户需求： $ARGUMENTS
