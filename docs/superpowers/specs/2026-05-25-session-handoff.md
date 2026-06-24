# 2026-05-25 Session Handoff

## 本次完成

- 完成设备清单主数据功能闭环：
  - `EquipmentCatalog` 实体、仓库、服务、控制器
  - `/api/equipment-catalog` CRUD
  - `/api/equipment-catalog/import`
  - `/api/equipment-catalog/export`
- 新增设备清单页面：
  - `aps-system/src/main/resources/static/12-equipment-catalog.html`
- 新增设备清单模板：
  - `template-equipment-catalog.xlsx`
  - `aps-system/src/main/resources/static/template-equipment-catalog.xlsx`
- 设备分析页已接入设备台账，命中规则为：
  - `manufacturingDepartment + equipment`
- 左侧栏已补入“设备清单”，并把相关页面能力测算菜单统一为：
  - `工时分析`
  - `设备分析`

## 已确认状态

- 本地服务已成功启动并验证过页面返回内容。
- 服务地址：
  - `http://172.21.186.253:8081`
- 已跑通过的测试：

```powershell
C:\tools\apache-maven-3.9.15\bin\mvn.cmd -Dtest=EquipmentCatalogServiceTest,EquipmentCatalogControllerTest,EquipmentLoadServiceTest,EquipmentLoadControllerTest test
```

## 当前业务口径

### 工时分析

- 输入：`planQty`, `staffCount`, `process`, `equipment`, `yearMonth`
- 当前公式：
  - `workforceDemand = planQty × staffCount`
- 聚合维度：
  - `process + equipment + yearMonth`

### 设备分析

- 输入：`planQty`, `cycleTime`, `moldCavity`, `manufacturingDepartment`, `equipment`, `yearMonth`
- 设备台账输入：`equipmentCategory`, `equipmentBrand`, `equipmentCount`
- 当前页面公式：
  - `requiredSeconds = (planQty × cycleTime) / moldCavity`
  - `availableSeconds = workDays × dailyEffectiveHours × 3600`
  - `requiredMachineCount = requiredSeconds / availableSeconds`
  - `loadRate = requiredMachineCount / equipmentCount`

## 下次优先做

1. 写正式的“工时分析 / 设备分析”计算口径文档。
2. 用 TDD 改造后端 `EquipmentLoadService`，让后端口径与当前设备分析页一致。
3. 补针对设备台账命中/未命中的 `EquipmentLoadServiceTest`。

## 注意事项

- 静态页改完后一定要重启服务，否则浏览器看到的可能还是旧资源。
- `schema.sql` / `schema-current.sql` 当前更像示例数据快照，不是唯一 DDL 来源。
