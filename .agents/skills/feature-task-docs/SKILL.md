---
name: feature-task-docs
description: >-
  Author professional feature documentation under docs/features/F{nnn}-{slug}/:
  TASK.md from TASK-template, alignment with plan.md (approved), contract.md,
  test-plan.md, stable AC-xx IDs, and traceability to tests. Use when creating
  or updating task docs, feature briefs, acceptance criteria, or feature folder
  structure before or during /build-feature. TRIGGER when: user asks for TASK
  document, feature spec, F-directory docs, AC list, or "写任务文档/功能文档".
  DO NOT TRIGGER when: user only wants code with no documentation deliverable.
---

# Feature Task 文档（TASK / 功能目录）

本 Skill 约束 **WMS 仓库** 中 `docs/features/F{三位序号}-{feature-slug}/` 下的任务与配套文档写法，使文档可评审、可自动化追溯、可与 Agent 命令（`/plan-feature`、`/build-feature`）衔接。

## 1. 文档角色与顺序

| 文件 | 阶段 | 作用 |
|------|------|------|
| `plan.md` | **先于** TASK | 方案与范围；人审通过后 `plan_status: approved`（见 `/plan-feature`） |
| `TASK.md` | `/build-feature` Phase 1 | 需求、范围、技术分析、**AC-xx**、风险、进度表 |
| `contract.md` | Phase 2 | 前后端契约（冻结后开发） |
| `test-plan.md` | Phase 3 | P0/P1 用例，**引用相同 AC-xx** |
| `reports/` | 联调/审查/QA | 流程类产出 |

**硬规则**：新建功能时，**不得**在 `plan.md` 未批准的情况下把 `TASK.md` 标为可进入契约/开发（与 [`build-feature`](../../commands/build-feature.md) Phase 0.5 一致）。

## 2. 创建或更新前的输入

向用户或需求来源确认（信息不足则先提问，避免杜撰）：

1. **功能目录**：`F{nnn}-{feature-slug}` 或完整路径 `docs/features/F{nnn}-{feature-slug}/`；若新建目录，序号来自 [`docs/features/NEXT_FEATURE_NUMBER.txt`](../../../docs/features/NEXT_FEATURE_NUMBER.txt)，建目录后递增该文件。
2. **与 plan.md 的关系**：若目录已存在，**Read** `plan.md`，TASK 的 In Scope / Technical Analysis / AC 须与方案一致；若有冲突，在 TASK「Notes → Decisions」中写明如何对齐。
3. **验收口径**：谁验收、是否已有接口/页面草图、非功能要求（性能、权限、审计）。

## 3. 编写 TASK.md 的步骤

1. **Read** [`docs/features/TASK-template.md`](../../../docs/features/TASK-template.md)，在目标目录保存为 **`TASK.md`**（不修改仓库根部的 `TASK-template.md`）。
2. **Metadata**：填 Feature、ID（`TASK-{slug}` 或团队约定）、Status（初稿多为 `draft`）、日期、Owner；保留「前置：plan.md 已批准」行，若 plan 尚未批准则注明阻塞原因。
3. **§1 Requirement Summary**：User Story 一条写全；Business Value 可量化或写清「为什么现在做」。
4. **§2 Scope**：In Scope 用可勾选列表，粒度为「可交付能力」；Out of Scope 明确写清，避免范围蠕变。
5. **§3 Technical Analysis**：Backend / Frontend / Database 与代码库真实模块对齐；**不得**发明不存在的包名或表名——不确定则写「待确认」并列入 Questions。
6. **§4 Acceptance Criteria**：
   - 每条使用稳定 ID：**`AC-01`、`AC-02`…**（两位数，连续优先）。
   - 描述应 **可验证**：明确触发条件、期望结果、错误/权限行为（如适用）。
   - 与同目录后续 **`test-plan.md`** 及自动化测试中的字符串引用 **一致**（`check-task-traceability.sh` 会检查 AC 是否在测试中出现）。
7. **§5–6**：依赖与风险写具体对象（服务名、迁移编号、外部系统），避免空话。
8. **§7 Progress**：阶段与仓库 Phase 对齐；Deliverables 路径使用真实 `F{nnn}-{slug}`。
9. **§8 Notes**：决策记 **原因**；未决问题单列，不混在 AC 里。
10. **Change Log**：首条填 Initial 与日期。

## 4. 质量门槛（写完自检）

- [ ] 每个 **AC-xx** 在字面上可被测试或演示验证。
- [ ] 无与 `plan.md` / 已有 `contract.md` 的未解释矛盾。
- [ ] 目录内文件名与 [`docs/features/README.md`](../../../docs/features/README.md) 约定一致。
- [ ] 若 TASK 含 AC-xx：后续补测试后运行 `node tools/ai-scaffold/dist/cli.js check-task-traceability docs/features/F{nnn}-{slug}` 应能通过（或明确标注暂不可追溯项与原因）。

## 5. 反模式（避免）

- **模糊 AC**：如「系统稳定」「体验良好」——改为可观测条件。
- **ID 漂移**：正文、test-plan、测试代码三处 AC 编号不一致。
- **跳过 plan**：新功能无已批准 `plan.md` 即写满 TASK 并宣称可开发。
- **模板留空**：大量 `{占位符}` 未替换即提交。

## 6. 与命令、脚本的衔接

- 方案阶段：`/plan-feature` → 人审 `plan.md`。
- 开发入口：`/build-feature`（Phase 0.5 执行 `node tools/ai-scaffold/dist/cli.js check-plan-approved docs/features/F{nnn}-{slug}`）。
- 可追溯：`node tools/ai-scaffold/dist/cli.js check-task-traceability docs/features/F{nnn}-{slug}`（TASK 中 AC 与测试引用）。

## 7. 简短示例（Metadata + AC 片段）

```markdown
## Metadata
- Feature: F004-inventory-alert
- ID: TASK-inventory-alert
- Status: draft
- **前置**：同目录 plan.md 已人审通过（plan_status: approved）

## 4. Acceptance Criteria
- [ ] **AC-01**: 当库存低于安全库存时，系统在 1 分钟内生成待处理记录且列表可见。
- [ ] **AC-02**: 无「库存预警」权限的用户调用相关 API 返回 403，前端提示无权限。
```

按本 Skill 产出文档后，应在会话中说明：**下一步**为契约（`contract.md`）或按 `build-feature` 进入对应 Phase。
