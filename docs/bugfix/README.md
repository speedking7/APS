# Bugfix Docs

每个缺陷使用独立目录 `docs/bugfix/{bug-id}-{slug}/`，统一沉淀复现信息、修复范围、测试计划、SQL 和验证证据。
`docs/bugfix/` 根目录只保留说明和模板，不能把多个 bug 的文档直接堆在同一个目录里。

## Required Files

| File | Purpose |
|------|---------|
| `bug.md` | 现象、影响、复现路径、根因、修复范围、回滚说明 |
| `test-plan.md` | 复现检查、回归检查、自动化与手工验证范围 |
| `sql/` | 本 bugfix 需要执行的 SQL 与测试数据 SQL |
| `reports/` | 日志、截图、review、QA、验证记录 |

## Conventions

- 目录命名使用 `docs/bugfix/{bug-id}-{slug}/`，`bug-id` 优先使用真实缺陷编号或工单号
- 一次 bugfix 对应一个独立目录，禁止多个 bug 共用同一个目录
- 不要只把根因和验证记录留在聊天记录里，必须落到 bugfix 目录
- 若 bugfix 涉及数据库变化，执行 SQL 和测试数据 SQL 必须归档到 `sql/`
- 若 bugfix 改动 `frontend/`，需要在 `reports/` 中保留对应验证证据

## Example Layout

```text
docs/bugfix/
├── README.md
├── bug-template.md
├── test-plan-template.md
├── BUG-1023-role-id-precision/
│   ├── bug.md
│   ├── test-plan.md
│   ├── sql/
│   └── reports/
└── BUG-1057-factory-edit-dialog/
    ├── bug.md
    ├── test-plan.md
    ├── sql/
    └── reports/
```

## Suggested Workflow

1. 创建或定位 `docs/bugfix/{bug-id}-{slug}/`
2. 先写 `bug.md`
3. 再写 `test-plan.md`
4. 补充自动化测试并实现修复
5. 将验证证据归档到 `reports/`
