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
| **基础数据域（MD）** | 管理预测、BOM、报废率、库存天数、稼动天数、盘点数 |
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

`--spring.sql.init.mode=always` 会在启动时执行 `schema.sql` 写入示例数据。  
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

项目根目录提供了 `APS模板.xlsx`，包含 6 张输入表。

### Excel 各 Sheet 对应关系

| Sheet 名称 | 对应数据 | 必填列 |
|-----------|---------|--------|
| `预测（仅完成品）` | 完成品月度预测量 | 存货编码、年月(YYYYMM)、数量 |
| `报废率` | 各物料报废率 | 存货编码、报废率(0~1) |
| `库存天数` | 安全库存天数 | 存货编码、安全天数、最大天数 |
| `稼动天数` | 各月实际工作天数 | 年月(YYYYMM)、天数 |
| `BOM` | 物料清单及工艺参数 | 父零件、子零件、用量、工序、设备、模腔数、制造周期、持台人数、单件节拍 |
| `盘点数` | 期初可用库存 | 存货编码、年月（底）(YYYYMM)、可用量 |

### 填写规范

- **年月格式**：6 位整数，如 `202604` 表示 2026 年 4 月
- **BOM 叶节点**：子零件列可为空，代表该物料为最底层物料
- **报废率**：填写小数，如 `0.01` 表示 1%
- **删除重写标记**：可随意填写分类标签，不影响计算

### 导入操作

填写好 Excel 后，使用以下命令一键导入（**覆盖模式，会清空现有数据**）：

```bash
curl -X POST http://localhost:8080/api/excel/import \
  -F "file=@/path/to/APS模板.xlsx"
```

成功响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "forecastCount": 6,
    "scrapRateCount": 6,
    "inventoryDaysCount": 6,
    "operatingDaysCount": 3,
    "bomCount": 6,
    "inventoryCountCount": 6
  }
}
```

也可用任意 HTTP 客户端（Postman、Apifox 等）发送 `multipart/form-data` 请求，参数名为 `file`。

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

```bash
curl -X POST http://localhost:8080/api/excel/import \
  -F "file=@APS模板.xlsx"
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
| POST | `/api/excel/import` | 上传 Excel 文件，全量覆盖导入 6 张数据表 |

请求：`multipart/form-data`，参数名 `file`

### 7.2 基础数据 CRUD

以下资源均支持标准 CRUD 接口（`{resource}` 替换为对应路径）：

| 资源路径 | 数据内容 |
|---------|---------|
| `forecast` | 完成品预测 |
| `scrap-rate` | 报废率 |
| `inventory-days` | 库存天数 |
| `operating-days` | 稼动天数 |
| `bom` | BOM 物料清单 |
| `inventory-count` | 期初盘点数 |

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

availableTimeHours = 稼动天数 × 10.5小时/天

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
A：确保 Sheet 名称与模板完全一致，包括括号和空格：`预测（仅完成品）`、`BOM`（大写）等。

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

# 1. 导入 Excel
curl -X POST $BASE/api/excel/import -F "file=@APS模板.xlsx"

# 2. 触发计划计算
curl -X POST $BASE/api/production-plan/calculate

# 3. 查询 202604 期间的生产计划
curl "$BASE/api/production-plan/by-period/202604"

# 4. 查询人员需求报表
curl "$BASE/api/workforce-report?periods=202604,202605,202606"

# 5. 查询设备负荷报表
curl "$BASE/api/equipment-load?periods=202604,202605,202606"
```
