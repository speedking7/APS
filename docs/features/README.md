# Feature Docs

每个功能使用独立目录 `F{nnn}-{feature-slug}/`，目录中集中维护方案、任务、契约、测试计划和流程报告。

## Required Files

| File | Purpose |
|------|---------|
| `plan.md` | 功能方案，必须经人工审批后才能进入实现 |
| `TASK.md` | 范围、AC、风险、DoD、进度跟踪 |
| `contract.md` | 冻结后的前后端契约 |
| `test-plan.md` | P0/P1 用例与 AC 映射 |
| `sql/` | 本 feature 需要执行的 SQL 与测试数据 SQL |
| `reports/` | 联调、Review、QA、E2E 等全部过程产物 |

## Planning Evidence Archive

`/plan-feature` 阶段除了 `plan.md` 外，还必须把 interview / planning 证据按功能目录归档到 `reports/planning/`：

| File | Purpose |
|------|---------|
| `reports/planning/deep-interview.md` | `deep-interview` 的正式澄清结论归档 |
| `reports/planning/prd.md` | `$ralplan` 生成的 PRD / planning summary 归档 |
| `reports/planning/test-spec.md` | `$ralplan` 生成的测试规划 / test-spec 归档 |

## Templates

`node tools/ai-scaffold/dist/cli.js init-feature --slug <slug> --title <title>` 会从以下模板生成新功能目录：

- [`plan-template.md`](plan-template.md)
- [`TASK-template.md`](TASK-template.md)
- [`contract-template.md`](contract-template.md)
- [`test-plan-template.md`](test-plan-template.md)

## Validation

- Archive deep-interview evidence: `node tools/ai-scaffold/dist/cli.js archive-planning-artifacts docs/features/F{nnn}-{slug} --stage deep-interview`
- Archive ralplan evidence: `node tools/ai-scaffold/dist/cli.js archive-planning-artifacts docs/features/F{nnn}-{slug} --stage ralplan`
- Archive all planning evidence: `node tools/ai-scaffold/dist/cli.js archive-planning-artifacts docs/features/F{nnn}-{slug}`
- Plan approval: `node tools/ai-scaffold/dist/cli.js check-plan-approved docs/features/F{nnn}-{slug}`
- Build-feature prerequisites: `node tools/ai-scaffold/dist/cli.js check-build-feature-prereqs docs/features/F{nnn}-{slug}`
- Contract status: `node tools/ai-scaffold/dist/cli.js verify-contract docs/features/F{nnn}-{slug}`
- AC traceability: `node tools/ai-scaffold/dist/cli.js check-task-traceability docs/features/F{nnn}-{slug}`
- Feature artifacts: `node tools/ai-scaffold/dist/cli.js check-feature-artifacts docs/features/F{nnn}-{slug}`
- Scaffold gate: `node tools/ai-scaffold/dist/cli.js gate --feature-dir docs/features/F{nnn}-{slug}`
- Render child-agent prompt with skills: `node tools/ai-scaffold/dist/cli.js render-agent-prompt --role <role> --feature-dir docs/features/F{nnn}-{slug} --task "<task>" --summary`
- Reuse evidence is part of the `--feature-dir` gate; missing reuse sections in `plan.md` / `TASK.md` now fail validation
- Work item link: `git diff --name-only <base>...HEAD | node tools/ai-scaffold/dist/cli.js check-work-item-link --stdin`
- Reuse duplication scan: `git diff --name-only <base>...HEAD | node tools/ai-scaffold/dist/cli.js check-reuse-duplication --stdin --base <base> --head HEAD --range-mode three-dot`
- Scaffold self-tests: `npm --prefix tools/ai-scaffold test`

## Conventions

- `plan.md` 不是纯方案摘要，必须在计划阶段显式写出 `Intent`、`Non-goals`、`Decision Boundaries`、`Exception Scenarios`
- `plan.md` 必须包含独立 `Reuse Strategy` / `复用策略` 章节，说明要复用的既有能力、禁止复制的平行实现，以及新增抽象的理由
- `TASK.md` 必须包含独立 `Reuse Plan` / `复用方案` 章节，并在 DoD 中勾选“复用审查已完成”
- 修改 `ai-scaffold.config.json` 中 `codeLikeRoots` 覆盖的代码路径时，必须同时更新对应 `docs/features/F{nnn}-*` 或 `docs/bugfix/*` 目录；否则 CI 会失败
- 复用章节必须写出具体 seam，例如代码路径、Service / Controller / DTO、API、SQL 表、权限码或测试文件；保留空模板会失败
- 脚手架模板不得残留 `{nnn}`、`{feature-slug}`、`{slug}` 等生成期占位符；`init-feature` 会在写入前校验这些 token
- 新 feature 的计划阶段必须先执行 `deep-interview`，再基于其 spec / interview 结论执行 `$ralplan`
- `deep-interview` 与 `$ralplan` 产物不得只留在 `.omx/`；必须归档到当前 feature 目录的 `reports/planning/`
- 异常场景边界未写清，或缺少 `reports/planning/` 中的规划证据，不得批准 `plan.md`
- 自动化测试文件应包含稳定追踪标签 `TASK-{feature-slug}`
- `TASK.md`、`test-plan.md`、自动化测试必须引用同一组 `AC-xx`
- 所有本 feature 需要执行的 SQL 与测试数据 SQL 都归档在 `sql/`
- 若修改配置中的 frontend 路径，合并前必须通过 Playwright E2E
- 所有测试报告必须归档到对应 feature 的 `reports/`，包含 `playwright-report`
- 每次新增功能必须先完成 `admin` 授权
- E2E 使用 `ai-scaffold.config.json` 中配置的账号执行

## Next Feature

- If the next feature must be chosen from `docs/IMPLEMENTATION-PLAN.md`, follow [`start-next-feature.md`](../workflow/start-next-feature.md)
- Helper command: `node tools/ai-scaffold/dist/cli.js start-next-feature --task-id <task-id> --slug <slug> --title <title>`
- Add `-Scaffold` when the selection is confirmed and the new feature directory should be created immediately
