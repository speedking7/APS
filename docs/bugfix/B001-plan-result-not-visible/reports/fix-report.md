# Bug Fix Report

## Bug Information
- ID: B001
- Title: 计算结果在数据库存在但前台计划结果页不显示
- Severity: Major
- Fixed: 2026-05-18

## Analysis
- Root Cause: `08-plan-result.html` 将 API 基址硬编码为 `http://localhost:8080`，但本地标准启动端口为 `8081`。页面加载时请求了错误端口，导致无法取得 `/api/production-plan` 数据。
- Affected Files:
  - `aps-system/src/main/resources/static/08-plan-result.html`
- Related Runtime Context:
  - 本地服务运行在 `http://localhost:8081/`
  - 数据库 `aps_db.t_production_plan` 中已有 29 条计划结果记录

## Solution
- Approach: 将计划结果页 API 基址改为 `location.origin`，与其他静态页面保持一致，让页面自动使用当前访问来源和端口。
- Changes Made:
  - `aps-system/src/main/resources/static/08-plan-result.html`: `const API = 'http://localhost:8080';` 改为 `const API = location.origin;`
  - `aps-system/target/classes/static/08-plan-result.html`: 同步运行目录静态资源
  - `aps-system/target/aps-system-1.0.0.jar`: 同步内嵌静态资源，已备份为 `target/aps-system-1.0.0.jar.bak-B001`

## Testing
- [x] 复现测试已添加：`reports/repro-static-api-check.ps1`
- [x] 红灯验证：修复前脚本检测到硬编码 `http://localhost:8080`
- [x] 绿灯验证：修复后脚本通过
- [x] 集成验证：`GET http://localhost:8081/api/production-plan` 返回 `code=200` 且 `data` 共有 29 条
- [x] 静态页面验证：`GET http://localhost:8081/08-plan-result.html` 包含 `const API = location.origin`，不再包含 `localhost:8080`
- [x] 数据库验证：`sql/production-plan-evidence.txt` 记录 `t_production_plan` 有 29 条

## Contract Change
- [x] 不涉及契约变更
- [ ] 契约已更新

## Verification
- [x] Bug 已修复
- [x] 无后端接口变更
- [x] 文档与证据已更新

## Notes
如果后续重新打包 Jar，需要确保源码中的 `08-plan-result.html` 已包含本次修复；当前源码已修复。
