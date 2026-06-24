# Project Guide

本文件记录 APS 项目的业务背景、项目结构、技术栈、运行方式和工程约定。`AGENTS.md` 是顶层执行规则，本文件作为项目级补充说明。

## Project Overview

APS（Advanced Planning and Scheduling，高级计划排程）是一个面向制造计划的单体 Web 应用，用于维护基础数据、执行生产计划计算并查看计划结果、人员需求和设备负荷。

核心能力：

- 预测需求管理
- BOM 管理
- 设备清单维护
- 物料参数维护（安全库存、报废率等）
- 稼动天数维护
- 库存盘点数维护
- 生产计划计算
- 计划结果查询
- 人员需求报表
- 设备负荷报表
- Excel 模板下载与导入

## Technology Stack

### Backend

- Java 11 源码级别（当前本机可用运行时为 Java 17）
- Spring Boot 2.7.18
- Spring Web MVC：REST API 与静态资源托管
- Spring Data JPA / Hibernate：数据访问与 ORM
- MySQL 8.x：本地开发数据库
- Lombok：实体和 DTO 样板代码简化
- Apache POI 5.2.5：Excel 导入/模板处理
- Maven：构建与打包

### Frontend

- 静态 HTML/CSS/JavaScript 页面
- 前端文件由 Spring Boot 从 `src/main/resources/static/` 直接托管
- 页面使用浏览器原生 `fetch` 调用后端 API
- API 基址约定使用 `location.origin`，避免端口硬编码

### Database

- MySQL 数据库名：`aps_db`
- 本地 MySQL 运行在 WSL 中
- 数据库连接通过环境变量覆盖：
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `SPRING_SQL_INIT_MODE`
- JPA 配置：`spring.jpa.hibernate.ddl-auto=update`
- 初始化数据脚本：`aps-system/src/main/resources/data-seed.sql`
- 结构/历史脚本：
  - `aps-system/src/main/resources/schema.sql`
  - `aps-system/src/main/resources/schema-current.sql`

### Test Stack

- Spring Boot Test
- JUnit 5
- H2（测试作用域）
- MockMvc controller 测试
- Service 与 integration 测试位于 `aps-system/src/test/java/`

### Deployment / Runtime

- Spring Boot 可执行 Jar：`aps-system/target/aps-system-1.0.0.jar`
- Dockerfile：`aps-system/Dockerfile`
- Docker Compose：`aps-system/docker-compose.yml`
  - `aps-mysql`：MySQL 8.0
  - `aps-app`：Spring Boot 应用

## Repository Structure

```text
C:\work\aps
├── AGENTS.md                         # AI agent 顶层操作规则
├── project.md                        # 当前项目说明（本文档）
├── ai-scaffold.config.json           # AI scaffold 项目适配配置
├── README.md                         # scaffold/迁移说明，非主要业务说明
├── DEPLOY.md                         # 部署相关说明
├── 操作手册.md                       # 业务操作手册
├── e2e_test.py                       # 端到端/冒烟测试脚本
├── template-*.xlsx                   # 根目录业务导入模板副本
├── aps-system/                       # APS 主应用模块
├── docs/                             # 项目文档、功能计划、bugfix 证据
├── prototypes/                       # 原型/设计材料
├── scripts/                          # 辅助脚本
├── test-results/                     # 测试产物
├── tools/ai-scaffold/                # AI scaffold CLI
├── .agents/                          # agent 角色与技能定义
├── .codex/                           # Codex 工作流入口
└── .omx/                             # OMX 运行态上下文，不作为正式交付物
```

## Application Module Structure

```text
aps-system/
├── pom.xml                           # Maven 项目定义
├── Dockerfile                        # 应用镜像构建文件
├── docker-compose.yml                # app + mysql 本地容器编排
├── README.md                         # APS 应用说明
├── server.log / server.err.log       # 本地启动日志
├── target/                           # 构建产物与当前可运行 Jar
└── src/
    ├── main/
    │   ├── java/com/aps/
    │   │   ├── ApsApplication.java   # Spring Boot 入口
    │   │   ├── common/               # 通用响应对象
    │   │   ├── controller/           # REST 控制器
    │   │   ├── dto/                  # 请求/响应 DTO
    │   │   ├── entity/               # JPA 实体
    │   │   ├── exception/            # 全局异常处理
    │   │   ├── repository/           # Spring Data JPA Repository
    │   │   └── service/              # 业务服务与计算逻辑
    │   └── resources/
    │       ├── application.yml       # 应用配置
    │       ├── data-seed.sql         # 初始化数据
    │       ├── schema.sql            # 数据库结构脚本
    │       ├── schema-current.sql    # 当前结构/示例脚本
    │       └── static/               # 前端静态页面和 Excel 模板
    └── test/
        ├── java/com/aps/             # controller/service/integration 测试
        └── resources/application.yml # 测试配置
```

## Backend Package Responsibilities

- `com.aps.ApsApplication`：应用启动入口。
- `common`：统一 API 响应结构，例如 `ApiResponse`。
- `controller`：HTTP API 层，负责 CRUD、导入、计算触发、报表查询等入口。
- `dto`：请求 DTO，例如计划计算请求 `CalculateRequest`。
- `entity`：JPA 实体，对应业务表。
- `repository`：JPA Repository，封装数据库访问。
- `service`：业务逻辑层，包括基础数据服务、计划计算服务、人员需求和设备负荷服务。
- `exception`：全局异常处理。

## Main Controllers

- `ForecastController`：预测需求
- `DemandController`：需求数据
- `BomController`：BOM 数据
- `SafetyStockController`：安全库存
- `ScrapRateController`：报废率
- `InventoryDaysController`：库存天数/物料参数
- `OperatingDaysController`：稼动天数
- `InventoryCountController`：盘点数
- `PartMasterController`：零件主数据
- `EquipmentCatalogController`：设备清单主数据
- `ProductionPlanController`：生产计划计算与结果查询
- `ExcelImportController`：Excel 导入
- `WorkforceReportController`：人员需求报表
- `EquipmentLoadController`：设备负荷报表

## Main Domain Tables / Entities

| Entity | Table | Purpose |
| --- | --- | --- |
| `Forecast` | `t_forecast` | 完成品预测需求 |
| `Demand` | `t_demand` | 需求数据 |
| `Bom` | `t_bom` | BOM 父子件、工序、设备、节拍等 |
| `SafetyStock` | `t_safety_stock` | 安全库存参数 |
| `ScrapRate` | `t_scrap_rate` | 物料报废率 |
| `InventoryDays` | `t_inventory_days` | 库存天数参数 |
| `OperatingDays` | `t_operating_days` | 月度稼动天数 |
| `InventoryCount` | `t_inventory_count` | 盘点库存 |
| `PartMaster` | `t_part_master` | 零件主数据 |
| `EquipmentCatalog` | `t_equipment_catalog` | 设备清单主数据 |
| `ProductionPlan` | `t_production_plan` | 生产计划计算结果 |

## Frontend Static Pages

静态页面位于 `aps-system/src/main/resources/static/`：

- `index.html`：原型/导航总览
- `01-dashboard.html`：工作台
- `02-forecast-list.html`：预测管理
- `03-bom-list.html`：BOM 管理
- `04-material-params.html`：物料参数
- `05-operating-days.html`：稼动天数
- `06-inventory-count.html`：盘点数
- `07-plan-calculate.html`：计划计算
- `08-plan-result.html`：计划结果
- `09-capacity-calendar.html`：产能日历/占位页面
- `10-workforce-report.html`：人员需求
- `11-equipment-load.html`：设备负荷
- `12-equipment-catalog.html`：设备清单
- `template-*.xlsx` / `APS导入模板.xlsx`：导入模板

Frontend convention:

- API base should use `location.origin`.
- Avoid hard-coding `http://localhost:8080` or `http://localhost:8081` in static pages.
- Static HTML changes must be made under `src/main/resources/static/`; if running directly from an existing Jar, rebuild or update/restart the Jar before manual verification.

## API Conventions

- Base URL in local standard runtime: `http://localhost:8081`
- Response envelope:

```json
{ "code": 200, "message": "success", "data": {} }
```

Typical resources:

```text
GET/POST/PUT/DELETE /api/forecast
GET/POST/PUT/DELETE /api/demand
GET/POST/PUT/DELETE /api/bom
GET/POST/PUT/DELETE /api/safety-stock
GET/POST/PUT/DELETE /api/scrap-rate
GET/POST/PUT/DELETE /api/inventory-days
GET/POST/PUT/DELETE /api/operating-days
GET/POST/PUT/DELETE /api/inventory-count
GET/POST/PUT/DELETE /api/part-master
GET                    /api/part-master/by-part-no/{partNo}
GET/POST/PUT/DELETE /api/equipment-catalog
POST                  /api/equipment-catalog/import
GET                   /api/equipment-catalog/export
POST              /api/production-plan/calculate
GET               /api/production-plan
GET               /api/production-plan/versions
GET               /api/workforce-report
GET               /api/equipment-load
```

Production plan query responses now return an enriched DTO rather than the bare `ProductionPlan` entity. In addition to the persisted plan fields, the API may include:

- Current row part master attributes:
  - `itemProductName`
  - `itemProductNo`
  - `itemProjectName`
- Finished product part master attributes:
  - `finishedProductName`
  - `finishedProductNo`
  - `finishedProjectName`

These fields are resolved dynamically from `t_part_master`. Missing part master rows do not fail the query; the enriched fields return `null`.

## Local Runtime

- Standard local startup guide: `docs/local-startup.md`
- Backend module: `aps-system`
- Standard local URL: `http://localhost:8081/`
- Database is MySQL in WSL; use WSL commands for database operations.
- `8080` may be occupied on the current Windows host; use `8081` for local manual verification.

## Common Local Commands

Prepare database:

```powershell
wsl bash -lc "mysql -uroot -proot -h 127.0.0.1 -e 'CREATE DATABASE IF NOT EXISTS aps_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'"
```

Check app:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8081/
Invoke-WebRequest -UseBasicParsing http://localhost:8081/api/production-plan
```

Check database:

```powershell
wsl bash -lc "mysql -uroot -proot -h 127.0.0.1 aps_db -e 'SHOW TABLES;'"
```

Stop local Java app:

```powershell
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
```

## Documentation and Workflow Surfaces

- Feature plans/tasks/contracts/test plans: `docs/features/F{nnn}-{slug}/`
- Bugfix artifacts: `docs/bugfix/{bug-id}-{slug}/`
- Local startup guide: `docs/local-startup.md`
- Scaffold migration notes: `docs/ai-scaffold-migration.md`
- Agent roles/skills: `.agents/`
- Codex workflows: `.codex/workflows/`
- Runtime state: `.omx/`（不作为正式交付物）

## Engineering Notes

- Prefer minimal, evidence-backed changes.
- For backend work, add or update tests under `aps-system/src/test/java/`.
- For frontend static-page fixes, verify both page source and corresponding API response.
- For MySQL inspection, use WSL commands as required by `AGENTS.md`.
- Keep generated logs and runtime state out of intentional code changes unless specifically needed as evidence.

## Scaffold Commands

- Build scaffold CLI: `npm --prefix tools/ai-scaffold run build`
- Check environment: `node tools/ai-scaffold/dist/cli.js doctor`
- Create feature docs: `node tools/ai-scaffold/dist/cli.js init-feature --slug <slug> --title <title>`
- Render child-agent prompt: `node tools/ai-scaffold/dist/cli.js render-agent-prompt --role <role> --feature-dir docs/features/F001-example --task "..." --summary`
- Copy scaffold into another repo: `node copy-scaffold.mjs --target <target-repo>` or `node tools/ai-scaffold/dist/cli.js copy-scaffold --target <target-repo>`
- Run scaffold tests: `npm --prefix tools/ai-scaffold test`
