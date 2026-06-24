# 零件主数据与计划结果增强设计

## 背景

当前 APS 系统中的核心业务编码包括：

- `itemCode`
- `finishedProductCode`
- `parentCode`
- `childCode`

这些编码在业务上都可以视为“零件号”。现有计划结果、人员需求和设备负荷报表主要展示编码本身，缺少更丰富的零件属性信息。用户需要新增一张独立的零件主数据表，用于维护以下属性：

- 零件号
- 产品名称
- 产品番号
- 项目

该主数据未来将通过接口与外部系统集成，不需要 Excel 导入导出逻辑，也不需要维护界面。本次除建表和后端基础接口外，还要求生产计划结果查询接口能直接返回两套零件属性，供后续报表使用：

- 当前行 `itemCode` 对应的零件属性
- 当前行 `finishedProductCode` 对应的零件属性

## 目标

本次变更实现以下目标：

1. 新增独立零件主数据表 `t_part_master`。
2. 为零件主数据提供基础后端 CRUD/查询接口，供后续外部系统集成调用。
3. 生产计划结果查询接口返回增强 DTO，在原有结果基础上补充两套零件主数据属性。
4. 缺失零件主数据时，不阻塞现有计划结果查询链路，扩展字段返回 `null`。

## 方案选择

采用方案 A：

- 零件主数据独立建表；
- 计划结果查询阶段动态关联零件主数据；
- 不将零件属性冗余落库到 `t_production_plan`。

选择原因：

- 零件主数据与计划结果职责边界清晰；
- 便于后续外部系统直接同步更新；
- 零件属性修改后，报表查询可即时读取最新值；
- 避免在 `t_production_plan` 中引入重复文本字段和同步一致性问题。

## 数据设计

### 零件主数据表

新增表：`t_part_master`

字段：

- `id bigint`：自增主键
- `part_no varchar(50) not null`：零件号
- `product_name varchar(200) not null`：产品名称
- `product_no varchar(100) not null`：产品番号
- `project_name varchar(100) not null`：项目

约束：

- `part_no` 唯一索引
- 业务字段全部必填

### 业务含义

- `part_no` 与系统现有的 `itemCode / finishedProductCode / parentCode / childCode` 直接对应；
- 不做版本号管理；
- 主数据始终按最新值覆盖使用。

## 后端模型设计

新增：

- `entity/PartMaster.java`
- `repository/PartMasterRepository.java`
- `service/PartMasterService.java`
- `controller/PartMasterController.java`

`PartMaster` 实体字段：

- `id`
- `partNo`
- `productName`
- `productNo`
- `projectName`

`PartMasterRepository` 至少提供：

- `Optional<PartMaster> findByPartNo(String partNo)`
- `List<PartMaster> findByPartNoIn(Collection<String> partNos)`
- `boolean existsByPartNo(String partNo)`

## 接口设计

### 零件主数据基础接口

提供以下接口：

- `GET /api/part-master`
- `GET /api/part-master/{id}`
- `GET /api/part-master/by-part-no/{partNo}`
- `POST /api/part-master`
- `PUT /api/part-master/{id}`
- `DELETE /api/part-master/{id}`

说明：

- 本次虽不做页面，但保留基础维护接口，便于后续外部系统直接推送或同步；
- `POST/PUT` 采用 JSON 请求体，不涉及文件上传。

### 计划结果增强返回

当前 `ProductionPlanController` 直接返回 `ProductionPlan` 实体列表。本次改为返回增强 DTO，例如 `ProductionPlanView`。

`ProductionPlanView` 包含两部分字段：

1. 原 `ProductionPlan` 的全部现有字段
2. 零件主数据扩展字段

扩展字段分为两套：

当前行 `itemCode` 对应：

- `itemProductName`
- `itemProductNo`
- `itemProjectName`

所属完成品 `finishedProductCode` 对应：

- `finishedProductName`
- `finishedProductNo`
- `finishedProjectName`

## 查询组装设计

### 组装原则

- 不修改 `PlanCalculationService` 的落库逻辑；
- 不在 `t_production_plan` 中冗余零件主数据文本；
- 在查询服务层完成零件主数据映射。

### 组装方式

1. 查询出 `ProductionPlan` 列表；
2. 收集所有 `itemCode` 与 `finishedProductCode`，合并去重；
3. 使用 `findByPartNoIn(...)` 一次性批量查询零件主数据；
4. 构建 `Map<String, PartMaster>`；
5. 将 `ProductionPlan` 转换为 `ProductionPlanView`，同时回填：
   - 当前行零件属性
   - 所属完成品零件属性

### 性能要求

- 禁止在循环中逐条查询零件主数据；
- 必须使用批量查询 + 内存映射；
- 这样可以保证计划结果量上来后不会出现明显 N+1 查询问题。

## 空值与缺失策略

如果 `itemCode` 或 `finishedProductCode` 在零件主数据表中找不到对应记录：

- 不抛错；
- 不影响原计划结果记录返回；
- 对应扩展字段返回 `null`。

这样可以保证：

- 主数据尚未同步完整时，报表仍可使用；
- 后续补齐主数据后，查询接口即可自动带出更多属性。

## 代码改动范围

新增文件：

- `aps-system/src/main/java/com/aps/entity/PartMaster.java`
- `aps-system/src/main/java/com/aps/repository/PartMasterRepository.java`
- `aps-system/src/main/java/com/aps/service/PartMasterService.java`
- `aps-system/src/main/java/com/aps/controller/PartMasterController.java`
- `aps-system/src/main/java/com/aps/dto/ProductionPlanView.java`

修改文件：

- `aps-system/src/main/java/com/aps/controller/ProductionPlanController.java`
- `aps-system/src/main/java/com/aps/service/ProductionPlanService.java`
- `aps-system/src/main/resources/schema.sql`
- `aps-system/src/main/resources/schema-current.sql`
- `aps-system/src/main/resources/data-seed.sql`（如需增加演示主数据）
- `project.md`（如需补充主实体/控制器说明）

## 测试设计

本次测试覆盖重点如下：

### 零件主数据接口/服务

- 创建零件主数据成功；
- 按 `partNo` 查询成功；
- 更新成功；
- 删除成功；
- `partNo` 唯一约束冲突时行为可控。

### 计划结果增强返回

重点验证：

1. `itemCode` 对应的零件属性能正确带出；
2. `finishedProductCode` 对应的零件属性能正确带出；
3. 同一结果行可以同时带出两套属性；
4. 缺失零件主数据时，扩展字段为 `null`，但原 `ProductionPlan` 数据仍正常返回。

## 非目标

本次不包含以下内容：

- 零件主数据 Excel 导入导出；
- 零件主数据前端维护页面；
- 外部系统真实对接实现；
- 人员需求、设备负荷页面直接展示零件主数据字段；
- 在 `t_production_plan` 中冗余保存零件名称、产品番号或项目。
