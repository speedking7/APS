# APS 系统部署与使用说明

## 目录

1. [系统概述](#1-系统概述)
2. [环境要求](#2-环境要求)
3. [本地部署（推荐：Docker 一键启动）](#3-本地部署推荐docker-一键启动)
4. [本地部署（手动安装）](#4-本地部署手动安装)
5. [数据录入：使用 Excel 模板导入](#5-数据录入使用-excel-模板导入)
6. [使用流程](#6-使用流程)
7. [API 接口说明](#7-api-接口说明)
8. [计算公式说明](#8-计算公式说明)
9. [常见问题](#9-常见问题)

---

## 1. 系统概述

APS（高级计划排程系统）用于汽车零部件工厂的月度生产计划管理，涵盖四个业务域：

| 域 | 说明 |
|----|------|
| **基础数据域（MD）** | 管理需求（完成品入库需求数）、BOM（含报废率）、半成品安全库存、稼动天数、半成品盘点数 |
| **生产计划域（PP）** | 多期多层 BOM 展开，自动计算每个物料每期的计划数量 |
| **能力人时域（CW）** | 基于计划数量和持台人数，测算一线人员需求 |
| **设备负荷域（EL）** | 基于计划数量和单件节拍，测算关键设备利用率 |

**核心数据流：**
```
Excel 模板导入 → 触发计划计算 → 查询计划结果 / 人员需求 / 设备负荷
```

---

## 2. 环境要求

| 组件 | 最低版本 | 说明 |
|------|---------|------|
| Java | 11+ | JDK，非 JRE |
| Maven | 3.6+ | 用于编译构建 |
| MySQL | 8.0+ | 数据库 |
| Docker（可选） | 20+ | 一键启动替代方案 |

检查当前环境：
```bash
java -version
mvn -version
mysql --version
docker --version
```

---

## 3. 本地部署（推荐：Docker 一键启动）

> 无需单独安装 MySQL，Docker 自动拉取并配置。

### 步骤一：进入项目目录

```bash
cd /path/to/APS/aps-system
```

### 步骤二：构建并启动

```bash
docker compose up --build -d
```

首次启动会自动：
- 拉取 MySQL 8.0 镜像
- 创建 `aps_db` 数据库
- 编译 Java 应用并打包
- 写入示例数据（schema.sql）

### 步骤三：验证启动成功

```bash
# 查看容器状态（两个容器均应为 Up）
docker compose ps

# 检查应用日志
docker compose logs app | grep "Started"
```

看到以下输出表示启动成功：
```
Started ApsApplication in X.XXX seconds
```

### 步骤四：访问系统

- 应用地址：`http://localhost:8080`
- MySQL：`localhost:3306`，账号 `root`，密码 `root`，数据库 `aps_db`

### 停止 / 重启

```bash
docker compose down       # 停止（保留数据库数据）
docker compose down -v    # 停止并清除数据库数据
docker compose up -d      # 重新启动
```

---

## 4. 本地部署（手动安装）

### 步骤一：准备 MySQL 数据库

```bash
# 登录 MySQL
mysql -uroot -p

# 创建数据库
CREATE DATABASE IF NOT EXISTS aps_db DEFAULT CHARACTER SET utf8mb4;
EXIT;
```

### 步骤二：克隆项目，进入目录

```bash
cd /path/to/APS/aps-system
```

### 步骤三：（可选）修改数据库连接

编辑 `src/main/resources/application.yml`，按实际情况修改：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aps_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root      # 改为你的 MySQL 用户名
    password: root      # 改为你的 MySQL 密码
```

### 步骤四：编译打包

```bash
mvn clean package -DskipTests
```

编译成功后会生成：`target/aps-system-1.0.0.jar`

### 步骤五：首次启动（自动建表 + 示例数据）

```bash
java -jar target/aps-system-1.0.0.jar --spring.sql.init.mode=always
```

> **建表说明**：应用启动时，Hibernate（`ddl-auto: update`）会根据 Entity 类自动在 `aps_db` 中创建所有数据表，无需手动执行任何 DDL 脚本。  
> `--spring.sql.init.mode=always` 的作用是在建表后额外执行 `src/main/resources/schema.sql`，写入预置示例数据。

**后续启动去掉此参数**，避免重复插入：

```bash
java -jar target/aps-system-1.0.0.jar
```

### 步骤六：后台运行（可选）

```bash
nohup java -jar target/aps-system-1.0.0.jar > aps.log 2>&1 &
echo "PID=$!"
```

查看日志：
```bash
tail -f aps.log
```

---

## 5. 数据录入：使用 Excel 模板导入

数据通过 **5 个独立 Excel 文件**分主题导入，每个文件对应一类业务数据，放置在项目根目录下。

### 5.1 文件清单与 Sheet 说明

#### APS_demand.xlsx — 完成品入库需求数

| Sheet 名称 | 必填列 |
|-----------|--------|
| `完成品入库需求数` | 客户、存货编码、年月（YYYYMM）、需求数量、期末库存、最小安全库存、完成品入库需求数、版本号 |

- **计划依据**：系统以 `完成品入库需求数` 列作为完成品的计划计算输入，该字段已综合考虑期末库存与最小安全库存；
- **导入规则**：按 **版本号 + 客户** 全删全导，同一版本号下的数据会先清空再写入；
- 完成品安全库存在本表 `最小安全库存` 列维护，不在半成品安全库存表中维护。

#### APS_bom.xlsx — BOM 物料清单

| Sheet 名称 | 必填列 |
|-----------|--------|
| `BOM` | 父零件、子零件、用量、工序、设备、模腔数/取数（pcs）、制造周期（S）、持台人数（人）、单件节拍（S）、报废率、版本号 |

- **报废率**：每行 BOM 独立维护该制造工步的报废率（0~1 小数），已从原独立报废率表合并入此；
- **导入规则**：按 **版本号** 全删全导。

#### APS_inventory.xlsx — 半成品期末盘点数

| Sheet 名称 | 必填列 |
|-----------|--------|
| `半成品期末盘点数` | 存货编码、年月（底）（YYYYMM）、可用量、版本号 |

- 仅维护半成品期末库存；完成品期末库存在 `APS_demand.xlsx` 中维护；
- **导入规则**：按 **版本号** 全删全导。

#### APS_safetystock.xlsx — 半成品安全库存

| Sheet 名称 | 必填列 |
|-----------|--------|
| `半成品安全库存` | 存货编码、每日当量、安全天数、最大天数、版本号 |

- 仅维护半成品安全库存；完成品安全库存在 `APS_demand.xlsx` 中维护；
- **导入规则**：按 **版本号** 全删全导。

#### APS_workingdays.xlsx — 稼动天数

| Sheet 名称 | 必填列 |
|-----------|--------|
| `稼动天数` | 年月（YYYYMM）、总出勤天数、工作日、双休日、国定节假日 |

- 计划计算使用 `工作日` 字段（总出勤天数的子集，剔除双休日和国定节假日）；
- **导入规则**：按 **年月** 新增或更新（upsert），不会清空其他期间数据。

### 5.2 填写规范

- **年月格式**：6 位整数，如 `202604` 表示 2026 年 4 月
- **BOM 叶节点**：子零件列可为空，代表该物料为最底层物料
- **报废率**：填写小数，如 `0.01` 表示 1%
- **版本号**：相同版本号数据会被全量替换，建议以日期或迭代号命名（如 `2026050101`）

### 5.3 导入操作

每个文件通过同一接口上传，以 `file` 参数指定对应文件。系统根据文件内 Sheet 名称自动识别数据类型：

```bash
# 导入需求数据
curl -X POST http://localhost:8080/api/excel/import \
  -F "file=@/path/to/APS_demand.xlsx"

# 导入 BOM 数据
curl -X POST http://localhost:8080/api/excel/import \
  -F "file=@/path/to/APS_bom.xlsx"

# 导入半成品期末盘点数
curl -X POST http://localhost:8080/api/excel/import \
  -F "file=@/path/to/APS_inventory.xlsx"

# 导入半成品安全库存
curl -X POST http://localhost:8080/api/excel/import \
  -F "file=@/path/to/APS_safetystock.xlsx"

# 导入稼动天数
curl -X POST http://localhost:8080/api/excel/import \
  -F "file=@/path/to/APS_workingdays.xlsx"
```

也可用任意 HTTP 客户端（Postman、Apifox 等）发送 `multipart/form-data` 请求，参数名为 `file`。

成功响应示例（以 BOM 导入为例）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "bomCount": 12
  }
}
```

---

## 6. 使用流程

### 标准操作流程

```
步骤 1  填写 Excel 模板
          ↓
步骤 2  POST /api/excel/import  导入数据
          ↓
步骤 3  POST /api/production-plan/calculate  触发计划计算
          ↓
步骤 4  查询结果
        ├── GET /api/production-plan              生产计划明细
        ├── GET /api/workforce-report             一线人员需求
        └── GET /api/equipment-load               设备负荷利用率
```

### 步骤 1：导入数据

依次导入 5 个 Excel 文件（顺序不限）：

```bash
curl -X POST http://localhost:8080/api/excel/import -F "file=@APS_demand.xlsx"
curl -X POST http://localhost:8080/api/excel/import -F "file=@APS_bom.xlsx"
curl -X POST http://localhost:8080/api/excel/import -F "file=@APS_inventory.xlsx"
curl -X POST http://localhost:8080/api/excel/import -F "file=@APS_safetystock.xlsx"
curl -X POST http://localhost:8080/api/excel/import -F "file=@APS_workingdays.xlsx"
```

### 步骤 2：触发计划计算

```bash
curl -X POST http://localhost:8080/api/production-plan/calculate
```

响应：
```json
{"code": 200, "message": "success", "data": "calculation completed"}
```

### 步骤 3：查询生产计划结果

```bash
# 查询全部计划
curl http://localhost:8080/api/production-plan

# 按期间查询（如 2026 年 4 月）
curl http://localhost:8080/api/production-plan/by-period/202604

# 按完成品查询
curl http://localhost:8080/api/production-plan/by-product/11201A012

# 按完成品 + 期间联合查询
curl http://localhost:8080/api/production-plan/by-product/11201A012/period/202604
```

返回字段说明：

| 字段 | 说明 |
|------|------|
| `finishedProductCode` | 完成品编码 |
| `itemCode` | 存货编码（含子件） |
| `yearMonth` | 期间（YYYYMM） |
| `process` | 工序 |
| `equipment` | 设备 |
| `currentInventory` | 当期期初库存 |
| `forecast` | 当期需求（完成品为预测，子件为父件计划数×用量） |
| `planQty` | **计划数量** |
| `isProduce` | 是否需要生产（Y/N） |
| `scrapRate` | 报废率 |
| `safetyDays` | 安全库存天数 |
| `operatingDays` | 稼动天数 |

### 步骤 4：查询一线人员需求报表

```bash
# 全部期间
curl http://localhost:8080/api/workforce-report

# 指定期间
curl "http://localhost:8080/api/workforce-report?periods=202604,202605"
```

返回字段说明：

| 字段 | 说明 |
|------|------|
| `process` | 工序 |
| `equipment` | 设备 |
| `yearMonth` | 期间 |
| `workforceDemand` | **人员需求合计**（= Σ 计划数量 × 持台人数） |

### 步骤 5：查询设备负荷报表

```bash
# 全部期间
curl http://localhost:8080/api/equipment-load

# 指定期间
curl "http://localhost:8080/api/equipment-load?periods=202604"
```

返回字段说明：

| 字段 | 说明 |
|------|------|
| `equipment` | 设备编码 |
| `process` | 工序 |
| `yearMonth` | 期间 |
| `taskTimeHours` | **任务时间**（小时）= Σ 计划数量 × 单件节拍 / 3600 |
| `availableTimeHours` | **可用时间**（小时）= 稼动天数 × 10.5 小时/天 |
| `utilizationRate` | **利用率**（0~1 小数，如 0.85 表示 85%） |
| `status` | 负荷状态 |

负荷状态说明：

| 状态 | 利用率区间 | 含义 |
|------|-----------|------|
| `LOOSE` | < 50% | 宽松，产能充裕 |
| `NORMAL` | 50% ~ 85% | 正常 |
| `TIGHT` | 85% ~ 100% | 紧张，需关注 |
| `OVERLOADED` | ≥ 100% | 超载，产能瓶颈 |

---

## 7. API 接口说明

### 7.1 Excel 导入

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/excel/import` | 上传单个 Excel 文件，系统按 Sheet 名称自动识别数据类型并按对应导入规则处理 |

请求：`multipart/form-data`，参数名 `file`。5 个文件分别上传，每次调用处理一个文件。

### 7.2 基础数据 CRUD

以下资源均支持标准 CRUD 接口（`{resource}` 替换为对应路径）：

| 资源路径 | 数据内容 |
|---------|---------|
| `demand` | 完成品入库需求数（含客户、需求数量、期末库存、最小安全库存、版本号） |
| `safety-stock` | 半成品安全库存（含每日当量、安全天数、最大天数、版本号） |
| `operating-days` | 稼动天数（含总出勤天数、工作日、双休日、国定节假日） |
| `bom` | BOM 物料清单（含报废率、版本号，报废率为每制造工步独立字段） |
| `inventory-count` | 半成品期末盘点数（含版本号） |

```
GET    /api/{resource}           查询所有
GET    /api/{resource}/{id}      查询单条
POST   /api/{resource}           新增（JSON body）
PUT    /api/{resource}/{id}      修改（JSON body）
DELETE /api/{resource}/{id}      删除
POST   /api/{resource}/batch     批量新增（JSON 数组）
```

### 7.3 生产计划

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/production-plan/calculate` | 触发全量计划计算 |
| GET | `/api/production-plan` | 查询所有计划结果 |
| GET | `/api/production-plan/by-period/{yearMonth}` | 按期间查询 |
| GET | `/api/production-plan/by-product/{code}` | 按完成品查询 |
| GET | `/api/production-plan/by-product/{code}/period/{yearMonth}` | 按完成品+期间查询 |

### 7.4 能力人时

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/workforce-report` | `periods`（可选，逗号分隔） | 一线人员需求报表 |

### 7.5 设备负荷

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/equipment-load` | `periods`（可选，逗号分隔） | 设备负荷利用率报表 |

### 7.6 统一响应格式

所有接口均返回：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

错误时 `code` 为 400（参数错误）或 500（系统错误），HTTP 状态码始终为 200。

---

## 8. 计算公式说明

### 生产计划数量

```
planQty = (需求 / 稼动天数 × 安全天数 + 需求 - 当期库存) / (1 - 报废率)
```

- **需求来源**：完成品取 `APS_demand.xlsx` 中的 `完成品入库需求数` 列（已包含完成品安全库存逻辑）；子件需求由父件计划数量通过 BOM 用量推导；
- **报废率来源**：从对应 BOM 行取报废率（每制造工步独立，不再使用独立的报废率表）；
- 结果 < 0 时取 0
- 结果向上圆整为整数（`Math.ceil`）
- 报废率 = 100% 时强制 planQty = 0（避免除零）
- 稼动天数 = 0 时，安全库存项跳过，退化为 `(需求 - 库存) / (1 - 报废率)`

### 跨期库存递推

```
下期期初库存 = 本期planQty × (1 - 本期报废率) - 本期需求 + 本期期初库存
```

### 子件需求传递

```
子件需求 = 父件本期planQty × BOM用量
```

### 一线人员需求

```
workforceDemand = Σ (planQty × staffCount)
                  按 (工序, 设备, 期间) 分组聚合
```

### 设备任务时间

```
taskTimeHours = Σ (planQty × taktTime秒 / 3600)
                按 (设备, 期间) 分组聚合

availableTimeHours = 工作日 × 10.5小时/天
                  （工作日取 APS_workingdays.xlsx 中的 `工作日` 字段，
                    为总出勤天数中剔除双休日和国定节假日后的天数）

utilizationRate = taskTimeHours / availableTimeHours
```

---

## 9. 常见问题

**Q：导入 Excel 后计划结果没变化？**  
A：导入只写入基础数据，需要再调用 `POST /api/production-plan/calculate` 重新计算。

**Q：计划数量全为 0？**  
A：检查以下几项：
1. 稼动天数表是否有对应期间的数据
2. 报废率是否为 1.0（100% 报废会强制 planQty=0）
3. 期初盘点数是否远大于预测量（库存充足则不需生产）

**Q：Excel 导入提示 Sheet 找不到？**  
A：确保每个文件内的 Sheet 名称与规定完全一致（含全角括号和空格）：
- `APS_demand.xlsx` → Sheet `完成品入库需求数`
- `APS_bom.xlsx` → Sheet `BOM`（大写）
- `APS_inventory.xlsx` → Sheet `半成品期末盘点数`
- `APS_safetystock.xlsx` → Sheet `半成品安全库存`
- `APS_workingdays.xlsx` → Sheet `稼动天数`

**Q：BOM 展开结果中有循环引用警告？**  
A：系统会自动检测并跳过循环引用，在日志中输出 `Circular BOM detected` 警告，不影响其他物料计算。检查 BOM 数据中是否存在 A→B→A 的环形关系。

**Q：8080 端口被占用启动失败？**  
A：释放端口后重启：
```bash
# 查看占用端口的进程
lsof -i :8080
# 或强制释放
fuser -k 8080/tcp
```

**Q：如何修改每日默认作业时间（当前默认 10.5 小时）？**  
A：在 `EquipmentLoadService.java` 中修改常量：
```java
static final double DEFAULT_HOURS_PER_DAY = 10.5;  // 修改此值
```
修改后需重新编译打包：`mvn package -DskipTests`

---

## 附录：快速测试命令

```bash
BASE=http://localhost:8080

# 1. 导入 Excel（5 个文件分别上传）
curl -X POST $BASE/api/excel/import -F "file=@APS_demand.xlsx"
curl -X POST $BASE/api/excel/import -F "file=@APS_bom.xlsx"
curl -X POST $BASE/api/excel/import -F "file=@APS_inventory.xlsx"
curl -X POST $BASE/api/excel/import -F "file=@APS_safetystock.xlsx"
curl -X POST $BASE/api/excel/import -F "file=@APS_workingdays.xlsx"

# 2. 触发计划计算
curl -X POST $BASE/api/production-plan/calculate

# 3. 查询 202604 期间的生产计划
curl "$BASE/api/production-plan/by-period/202604"

# 4. 查询人员需求报表
curl "$BASE/api/workforce-report?periods=202604,202605,202606"

# 5. 查询设备负荷报表
curl "$BASE/api/equipment-load?periods=202604,202605,202606"
```
