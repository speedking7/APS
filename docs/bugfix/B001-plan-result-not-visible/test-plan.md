# Test Plan: B001 计算结果前台不显示

## Reproduction
1. 启动本地应用：`http://localhost:8081/`
2. 确认数据库 `aps_db.t_production_plan` 存在记录
3. 访问计划结果相关页面/API
4. 观察页面是否展示结果

## Verification Checks
- [ ] 数据库中存在生产计划结果记录
- [ ] 后端生产计划查询 API 返回 `code=200` 且 `data` 有数据
- [ ] 前端页面调用正确 API
- [ ] 前端字段映射与 API 响应一致
- [ ] 页面可展示数据库中的计算结果

## Regression Checks
- [ ] 首页仍可访问
- [ ] `/api/forecast` 正常
- [ ] `/api/operating-days` 正常
- [ ] 生产计划计算接口不受影响
