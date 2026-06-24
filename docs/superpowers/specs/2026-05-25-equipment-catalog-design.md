# 设备清单导入导出维护设计

## 背景

当前 APS 已有多类基础数据维护页面，但设备分析页仍缺少正式设备主数据台账。

现状如下：

- 设备分析页面中的设备品牌固定显示为“—”；
- 设备大类默认取计划结果中的 `process`；
- 设备小类默认取计划结果中的 `equipment`；
- 台数默认按 `1` 台参与测算。

用户需要新增一个“设备清单管理”功能，支持设备主数据的维护、Excel 导入导出，并让设备分析页优先使用真实设备台账数据。

## 目标

本次变更实现以下目标：

1. 新增设备清单主数据台账，字段为制造部门、设备大类、设备品牌、设备小类、台数。
2. 提供与现有主数据维护界面一致风格的设备清单管理页。
3. 支持单条新增、编辑、删除。
4. 支持 Excel 模板下载、批量导入、全量导出。
5. 设备分析页命中设备台账后，展示真实设备大类、品牌和台数。
6. 不改变现有设备负荷测算公式，只替换主数据来源。

## 方案选择

采用方案 A：

- 将设备清单建成无版本的设备主数据台账；
- 单条数据唯一键使用 `制造部门 + 设备小类`；
- Excel 导入按“制造部门”维度执行全删全导；
- 设备分析页按 `manufacturingDepartment + equipment` 命中设备台账。

选择该方案的原因：

- 与当前用户已确认的业务口径一致；
- 不引入版本管理，降低维护复杂度；
- 能尽快替换设备分析页里的默认兜底字段；
- 保留后续按更多字段扩展匹配规则的空间。

## 数据设计

新增设备清单表 `t_equipment_catalog`，字段如下：

- `id bigint primary key auto_increment`
- `manufacturing_department varchar(50) not null`
- `equipment_category varchar(100) not null`
- `equipment_brand varchar(100) not null`
- `equipment_model varchar(100) not null`
- `equipment_count int not null`

唯一约束：

- `uk_equipment_catalog_dept_model (manufacturing_department, equipment_model)`

Java 实体 `EquipmentCatalog` 新增字段：

- `manufacturingDepartment`
- `equipmentCategory`
- `equipmentBrand`
- `equipmentModel`
- `equipmentCount`

字段约束：

- 所有文本字段均为必填；
- `equipmentCount` 必须大于 `0`；
- 不增加版本号字段。

## Excel 模板与导入导出设计

### 模板设计

新增模板文件 `template-equipment-catalog.xlsx`。

Sheet 名称：

- `设备清单`

第一行说明：

- `说明：按制造部门全删全导；制造部门+设备小类唯一；台数必须大于0`

第二行表头列顺序：

1. 制造部门
2. 设备大类
3. 设备品牌
4. 设备小类
5. 台数

模板内放置少量示例数据，风格与现有模板一致。

### 导入规则

导入接口不复用现有 `/api/excel/import` 的多 Sheet 总导入逻辑，单独提供设备清单导入入口。

导入校验规则：

- 制造部门必填；
- 设备大类必填；
- 设备品牌必填；
- 设备小类必填；
- 台数必填，且必须为大于 `0` 的整数；
- 同一导入文件中，不允许出现重复的 `制造部门 + 设备小类`；
- 发现任意错误时，整次导入失败，不允许部分成功。

导入落库规则：

1. 先解析并校验整份文件；
2. 收集文件中出现的制造部门集合；
3. 按制造部门删除这些部门当前已有的设备清单数据；
4. 批量写入导入文件中的新数据；
5. 未出现在本次文件中的其他制造部门数据保持不变。

### 导出规则

提供设备清单导出接口，导出当前全部设备台账。

导出列顺序与导入模板一致，并保留首行说明，便于业务先导出、再修改、再导入。

## 程序逻辑设计

### 设备清单 CRUD

新增以下后端对象：

- `EquipmentCatalog` 实体
- `EquipmentCatalogRepository`
- `EquipmentCatalogService`
- `EquipmentCatalogController`

接口如下：

- `GET /api/equipment-catalog`
- `GET /api/equipment-catalog/{id}`
- `POST /api/equipment-catalog`
- `PUT /api/equipment-catalog/{id}`
- `DELETE /api/equipment-catalog/{id}`
- `POST /api/equipment-catalog/import`
- `GET /api/equipment-catalog/export`

新增与修改时都需要校验：

- `manufacturingDepartment + equipmentModel` 不可重复；
- `equipmentCount > 0`；
- 必填字段不可为空白。

### 设备分析接入

当前 `11-equipment-load.html` 中的设备分析逻辑需要改造为优先读取设备台账。

匹配规则固定为：

- `productionPlan.manufacturingDepartment` → `manufacturingDepartment`
- `productionPlan.equipment` → `equipmentModel`

命中设备台账时：

- `设备名称（大类）` 使用 `equipmentCategory`
- `设备品牌` 使用 `equipmentBrand`
- `设备型号（小类）` 使用 `equipmentModel`
- `台数` 使用 `equipmentCount`

未命中设备台账时：

- 保留当前兜底逻辑；
- `设备名称（大类）` 继续默认取 `process`；
- `设备品牌` 继续显示“—”；
- `设备型号（小类）` 继续默认取 `equipment`；
- `台数` 继续按 `1` 台计算；
- 页面说明文案改为显式提示“未命中设备台账时使用默认值”。

本次不修改设备分析的负荷计算公式，只替换设备主数据来源。

## 页面设计

新增页面为 `12-equipment-catalog.html`，整体风格复用现有 BOM / 盘点数主数据管理页。

页面内容包括：

- 顶部统计卡：
  - 制造部门数
  - 设备小类数
  - 总台数
- 筛选条件：
  - 制造部门
  - 设备大类
  - 设备品牌
  - 设备小类
- 工具栏：
  - 下载模板
  - 批量导入
  - 新增
  - 导出
- 列表字段：
  - 制造部门
  - 设备大类
  - 设备品牌
  - 设备小类
  - 台数
  - 操作
- 单条新增/编辑弹窗
- 导入弹窗，并明确提示“按制造部门全删全导”

导航将该页面归入“基础数据”分组，命名为“设备清单”。

## 测试设计

按 TDD 顺序实施：

1. 为 `EquipmentCatalogService` 增加 CRUD 测试：
   - 正常新增成功；
   - 重复的 `制造部门 + 设备小类` 被拒绝；
   - 非法台数被拒绝。
2. 为导入逻辑增加测试：
   - 正常导入成功；
   - 导入文件内重复键时报错；
   - 台数非正整数时报错；
   - 仅删除本次文件涉及制造部门的历史数据；
   - 其他制造部门数据不受影响。
3. 为导出逻辑增加测试：
   - 生成正确的 Sheet 名、说明行和列顺序。
4. 为设备分析增加回归测试：
   - 命中设备台账时，使用真实设备大类、品牌和台数；
   - 未命中设备台账时，保留当前默认值逻辑。

## 兼容性与迁移

- 需要新增数据库表结构与初始化 SQL；
- 需要新增静态模板文件 `template-equipment-catalog.xlsx`；
- 设备分析页在无设备台账数据时仍能工作，兼容当前演示数据；
- 本次不要求回填历史业务数据，只要求无台账时兜底展示可用。

## 非目标

本次不包含以下内容：

- 设备清单版本管理；
- 基于设备大类的设备分析匹配规则；
- 单独的设备品牌字典、设备大类字典；
- 更复杂的导入合并策略；
- 修改设备负荷测算公式；
- 设备清单与 BOM、工序、设备编码之间的自动映射治理。
