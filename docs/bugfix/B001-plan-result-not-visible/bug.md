# Bug: 计算结果在数据库存在但前台界面不显示

## Bug Information
- ID: B001
- Title: 计算结果在数据库存在但前台界面不显示
- Severity: Major
- Reported: 2026-05-18

## Symptoms
用户反馈生产计划计算结果已经写入数据库，但前台页面没有显示结果。

## Expected Behavior
前台计划结果页面应通过后端 API 正确查询并展示数据库中的 `t_production_plan` 计算结果。

## Actual Behavior
数据库内有计算结果，但前台界面未展示。

## Scope
- 前端静态页面与 API 调用
- 生产计划结果查询接口
- 数据库字段/API 响应字段映射

## Non-goals
- 不重做计算逻辑
- 不修改数据库账号和本地启动标准
