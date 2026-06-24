# BOM 驱动测试数据自动初始化设计

## 背景

当前 APS 本地启动链路支持通过 `data-seed.sql` 初始化一套固定测试数据，但这套数据存在两个问题：

1. 它与用户刚导入并确认可用的 BOM 数据不一致；
2. 它不能根据当前 BOM 自动补齐其余主数据，导致每次换一版 BOM 后，零件主数据、设备清单、安全库存、盘点数、需求等测试数据都要重新手工维护。

用户希望本地开发环境具备以下能力：

- 记住当前确认可用的一版 BOM 作为测试基线；
- 以后应用启动时，如果数据库为空，自动恢复这版 BOM；
- 再基于这版 BOM 自动补齐联调用的测试主数据；
- 已有手工导入或修改的数据不应被每次启动覆盖。

同时，用户明确要求：

- 自动补齐 `t_shared_mold_rule`
- 自动补齐 `t_demand`
- 不再自动维护 `t_forecast`

## 目标

本次变更实现以下目标：

1. 固化当前确认可用的一版 `t_bom` 为测试基线数据。
2. 在应用启动时增加“仅空表自动补数”的测试数据引导逻辑。
3. 以 BOM 为源，自动补齐以下测试表：
   - `t_part_master`
   - `t_equipment_catalog`
   - `t_safety_stock`
   - `t_operating_days`
   - `t_inventory_count`
   - `t_shared_mold_rule`
   - `t_demand`
4. 自动补数不覆盖已有手工数据。
5. `t_demand` 仅基于 BOM 根节点生成，保证与当前计划计算入口一致。
6. `t_forecast` 本次不再参与自动初始化，并明确标记为遗留链路。

## 非目标

本次不包含以下内容：

- 删除 `t_forecast` 表；
- 删除 `ForecastController / ForecastService / ForecastRepository`；
- 改造 `02-forecast-list.html` 页面；
- 修改首页或其他页面对 `/api/forecast` 的遗留依赖；
- 建立可配置的复杂测试数据模板管理系统；
- 为自动生成数据提供 UI 配置界面。

## 当前现状分析

### 计划计算真实入口

当前 `PlanCalculationService` 已经不读取 `t_forecast`，而是通过 `DemandRepository` 读取 `t_demand`：

- 以 `version` 过滤需求；
- 读取成品 `itemCode + yearMonth` 的 `netDemand`；
- 再沿 BOM 向下递推生成半成品与零件计划。

因此，从测试数据完整性角度看，`t_demand` 是必须自动补齐的，而 `t_forecast` 不是。

### 遗留链路

`t_forecast` 目前仍然存在于以下遗留链路中：

- 后端 CRUD：
  - `ForecastController`
  - `ForecastService`
  - `ForecastRepository`
  - `Forecast` 实体
- 前端残留：
  - `02-forecast-list.html`
  - `01-dashboard.html` 中对 `/api/forecast` 的调用

这意味着 `t_forecast` 还不能在本次直接删除，否则会引入额外 UI 与接口回归风险。

## 方案选择

采用方案 A：

- 将当前确认可用的 BOM 固化为仓库内测试基线文件；
- 在 Spring Boot 启动期增加一个测试数据引导器；
- 对目标表逐表执行“空表检查”；
- `t_bom` 空时先恢复 BOM 基线；
- 再基于当前数据库中的 BOM 自动生成其余测试表；
- 每张目标表只在为空时自动补数，不覆盖已有数据。

选择该方案的原因：

- 满足“记住这版 BOM”的需求；
- 保证空库启动时可复现；
- 不会破坏用户之后手工导入或修改的数据；
- 测试数据生成规则集中在代码中，后续替换 BOM 基线后仍可复用；
- 风险显著低于“本次同时彻底删除 forecast 链路”。

## 启动行为设计

### 启动入口

新增一个应用启动期的测试数据引导组件，例如：

- `ApplicationRunner`
或
- `CommandLineRunner`

该组件在应用启动后执行一次测试数据检查与补数流程。

### 触发条件

只对以下表执行“空表自动补数”：

- `t_bom`
- `t_part_master`
- `t_equipment_catalog`
- `t_safety_stock`
- `t_operating_days`
- `t_inventory_count`
- `t_shared_mold_rule`
- `t_demand`

规则：

- 表为空：自动补数
- 表非空：跳过

### 补数顺序

必须按以下顺序执行，保证派生关系正确：

1. `t_bom`
2. `t_part_master`
3. `t_equipment_catalog`
4. `t_operating_days`
5. `t_inventory_count`
6. `t_safety_stock`
7. `t_shared_mold_rule`
8. `t_demand`

原因：

- 其余测试表全部依赖 BOM；
- `t_demand` 最终需要根据 BOM 根节点生成；
- `t_part_master` 与 `t_equipment_catalog` 是其他页面联调的基础表。

## BOM 基线设计

### 目标

把用户当前确认可用的一版 BOM 固化进仓库，作为后续空库恢复的测试基线。

### 文件形式

推荐新增专用 SQL 或资源文件，例如：

- `aps-system/src/main/resources/bootstrap/test-bom-seed.sql`

该文件只负责初始化 `t_bom` 基线，不混杂其他测试表。

### 行为

当 `t_bom` 为空时：

1. 执行 BOM 基线导入；
2. 保证数据库中存在完整 BOM；
3. 后续所有测试表都从这份数据库中的 BOM 读取派生。

## 自动生成规则

### 1. `t_part_master`

数据来源：

- 收集 BOM 中全部 `parent_code`
- 收集 BOM 中全部非空 `child_code`
- 合并去重

生成规则：

- `part_no = 物料编码`
- `product_no = P-物料编码`
- `product_name` 使用稳定默认命名：
  - BOM 根节点：`成品-物料编码`
  - 既是子件又是父件：`半成品-物料编码`
  - 仅作为子件出现：`零件-物料编码`
- `project_name` 按根成品归组生成，例如：
  - 第一组根成品 → `项目A`
  - 第二组根成品 → `项目B`
  - 以此类推

设计意图：

- 保证所有计划结果中出现的物料都有主数据；
- 同时让页面展示具备可识别的默认名称。

### 2. `t_equipment_catalog`

数据来源：

- 从 BOM 中读取：
  - `manufacturing_department`
  - `equipment`

按 `(manufacturing_department, equipment)` 聚合。

生成规则：

- `manufacturing_department = BOM.manufacturing_department`
- `equipment_model = BOM.equipment`
- `equipment_category = 自动生成设备`
- `equipment_brand = AUTO`
- `equipment_count = 同组合在 BOM 中出现次数，最小为 1`

过滤规则：

- 跳过 `equipment` 为空的记录；
- 跳过 `manufacturing_department` 为空的记录。

设计意图：

- 让设备清单页与设备分析页在空库时仍能联调；
- 不引入复杂的品牌/分类推断逻辑。

### 3. `t_operating_days`

数据来源：

- 独立生成，不依赖 BOM 具体层级

生成规则：

- 固定生成未来 3 个月记录
- 每月 1 条
- 默认值：
  - `total_days = 26`
  - `work_days = 21`
  - `weekend_days = 5`
  - `holiday_days = 0`

起始月份：

- 取当前自然月或约定测试月，保持稳定实现即可；
- 关键是 `t_demand`、`t_safety_stock` 与 `t_inventory_count` 使用同一时间窗口。

### 4. `t_inventory_count`

数据来源：

- 仅对非根节点物料生成

原因：

- 根成品需求由 `t_demand` 驱动；
- 半成品/零件期初库存更符合当前联调使用方式。

生成规则：

- 生成 1 个基准月份库存
- `year_month = 需求起始月的前一月`
- `available_qty` 使用稳定小整数，例如按物料序号递增
- `version` 使用统一自动测试版本

### 5. `t_safety_stock`

数据来源：

- 仅对非根节点物料生成

生成规则：

- 对每个非根节点物料，生成未来 3 个月记录
- `daily_equivalent = 固定测试值或按物料序号稳定递增`
- `safety_days = 3`
- `max_days = 15`
- `version` 使用统一自动测试版本

设计意图：

- 满足 `PlanCalculationService#getSafetyStockQty(...)` 的查询需求；
- 让半成品安全库存页面启动即有可见数据。

### 6. `t_shared_mold_rule`

数据来源：

- 仅从 BOM 根节点生成

根节点定义：

- 作为 `parent_code` 出现
- 但从未作为任何非空 `child_code` 出现

生成规则：

- 若根节点数小于 2：不生成规则
- 若根节点数大于等于 2：
  - 按顺序相邻配对生成规则
  - 例如：
    - 第 1、2 个根成品生成 1 条
    - 第 3、4 个根成品生成 1 条
- 字段默认值：
  - `equipment_code = null`
  - `mold_code = null`
  - `enabled = true`
  - `remark = 自动生成测试规则`

设计意图：

- 仅生成少量演示数据；
- 不伪造复杂业务关系；
- 保证共模规则页与相关分析能力有基础测试记录。

### 7. `t_demand`

数据来源：

- 只对 BOM 根节点生成

原因：

- 当前计划计算就是以成品需求为入口；
- 若对全部物料生成需求，会导致半成品被错误视为独立需求源。

生成规则：

- 对每个根节点物料生成未来 3 个月记录
- 字段默认值：
  - `customer = AUTO`
  - `demand_qty = 稳定递增测试值`
  - `ending_inventory = 固定小值`
  - `min_safety_stock = 固定小值`
  - `net_demand = demand_qty - ending_inventory`
  - `version = 统一自动测试版本`

设计要求：

- `net_demand` 必须与 `demand_qty`、`ending_inventory` 保持一致；
- 同一版本下 `(item_code, year_month)` 保持唯一业务语义。

## 版本策略

自动生成的数据统一使用一个明确的测试版本，例如：

- `AUTO-SEED-20260527`

适用表：

- `t_inventory_count`
- `t_safety_stock`
- `t_demand`
- 以及其他存在版本字段的测试表

这样做的原因：

- 能明确区分自动补数与手工导入数据；
- 方便用户在计划计算页面选择这套版本；
- 便于后续排查测试数据来源。

## `t_forecast` 的处理策略

本次对 `t_forecast` 的处理结论：

- 不再参与自动初始化；
- 不作为计划计算测试主路径；
- 保留现有遗留接口与页面，避免本次扩大改动范围；
- 在后续独立任务中，再统一删除 `forecast` 前后端残留链路。

## 代码改动范围

预计新增：

- 测试数据启动引导组件
- BOM 基线资源文件
- BOM 派生测试数据生成服务

预计修改：

- `application.yml` 或相关启动配置（如有需要）
- `data-seed.sql`（若需要弱化旧固定种子角色）
- 本地启动文档
- 项目文档中关于测试数据与启动行为的说明

## 测试设计

### 启动补数行为

验证：

1. 目标表为空时，应用启动后会自动补数；
2. 目标表非空时，不会覆盖已有数据；
3. `t_bom` 空时，能先恢复 BOM 基线；
4. 其余测试表会基于 BOM 继续补齐。

### BOM 派生正确性

验证：

1. `t_part_master` 覆盖 BOM 中全部物料编码；
2. `t_equipment_catalog` 能按部门与设备聚合；
3. `t_shared_mold_rule` 仅从根节点生成；
4. `t_demand` 仅对根节点生成；
5. `t_inventory_count` 与 `t_safety_stock` 仅对非根节点生成。

### 幂等性

验证：

1. 启动多次不会重复插入同一批自动数据；
2. 已存在数据时不会被清空重建；
3. 空表恢复顺序稳定，不因启动次数产生漂移。

## 风险与约束

### 风险 1：自动生成规则与真实业务不完全一致

接受策略：

- 本次目标是提供稳定测试基线，不是完整业务建模；
- 所有默认值以“联调可用、稳定可复现”为优先。

### 风险 2：`t_forecast` 仍在前端残留

接受策略：

- 本次不碰 forecast 清理；
- 先保证 BOM 驱动测试主路径跑通；
- 后续独立移除 forecast 遗留链路。

### 风险 3：当前确认 BOM 的持久化方式需要稳定

缓解策略：

- 将 BOM 固化为仓库内基线资源，不依赖数据库当前瞬时状态；
- 后续若更换 BOM，再通过显式更新基线文件完成“记住”。

## 结论

本次采用“BOM 基线 + 启动期空表自动补数”的方式，满足以下核心诉求：

- 记住当前确认可用的一版 BOM；
- 启动时自动恢复可联调的完整测试数据；
- 不覆盖手工已存在数据；
- 以 `t_demand` 替代 `t_forecast` 作为真实测试主路径；
- 将 `forecast` 删除工作延后到独立任务中处理，控制风险。
