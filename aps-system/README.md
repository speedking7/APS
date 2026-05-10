# APS 高级计划排程系统

基于 Spring Boot + MySQL + JPA 的生产计划排程系统。

## 技术栈
- Spring Boot 2.7.18
- Spring Data JPA + Hibernate
- MySQL 8.x
- Lombok
- Maven, Java 11

## 业务模型

### 输入表
| 表名 | 说明 |
|------|------|
| `t_forecast` | 完成品预测（存货编码、年月、数量） |
| `t_scrap_rate` | 各物料报废率 |
| `t_inventory_days` | 安全库存天数 / 最大天数 |
| `t_operating_days` | 各月稼动天数 |
| `t_bom` | BOM 物料清单（含工序、设备、模腔、节拍等） |
| `t_inventory_count` | 期初盘点数 |

### 结果表
- `t_production_plan` - 生产计划结果集

## 计算逻辑

对每个完成品，按年月升序、按 BOM 树从父到子顺序计算：

1. **当前库存**
   - 第一期：取最新盘点 `t_inventory_count.available_qty`
   - 后续期：`上期planQty * (1 - 上期scrapRate) - 上期demand + 上期currentInventory`
2. **需求/预测**
   - 根节点（完成品）：取 `t_forecast.quantity`
   - 子件：`父节点本期planQty * BOM用量`
3. **计划数量**
   `planQty = (需求/稼动天数*安全天数 + 需求 - 当前库存) / (1 - 报废率)`
   小于 0 取 0
4. **是否生产** Y/N：`需求 > 当前库存 ? Y : N`

## 启动步骤

### 1. 准备 MySQL
```bash
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS aps_db DEFAULT CHARSET utf8mb4;"
```
（`application.yml` 中 `createDatabaseIfNotExist=true`，无需手动建库也可。）

### 2. 修改 `src/main/resources/application.yml`
按需调整数据库连接：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aps_db?...
    username: root
    password: root
```

### 3. 编译运行
```bash
cd aps-system
mvn clean package -DskipTests
mvn spring-boot:run
```
或直接：
```bash
java -jar target/aps-system-1.0.0.jar
```

### 4. 加载示例数据（可选）
首次启动 JPA 会自动建表。要加载示例数据：
- 在 `application.yml` 中将 `spring.sql.init.mode` 改为 `always`
- 取消注释 `data-locations: classpath:schema.sql` 行
- 重启应用一次（之后建议改回 `never` 防止重复插入）

或手动执行：
```bash
mysql -uroot -proot aps_db < src/main/resources/schema.sql
```

## REST API

### 通用 CRUD（每个输入表）
```
GET    /api/{resource}        - 查询所有
GET    /api/{resource}/{id}   - 查询单条
POST   /api/{resource}        - 新增（JSON body）
PUT    /api/{resource}/{id}   - 修改
DELETE /api/{resource}/{id}   - 删除
POST   /api/{resource}/batch  - 批量导入（JSON 数组）
```

资源路径：`forecast` / `scrap-rate` / `inventory-days` / `operating-days` / `bom` / `inventory-count`

### 生产计划
```
POST /api/production-plan/calculate            # 触发全量计算
GET  /api/production-plan                      # 查询所有
GET  /api/production-plan/by-period/{yearMonth}
GET  /api/production-plan/by-product/{code}
GET  /api/production-plan/by-product/{code}/period/{yearMonth}
```

### 统一返回格式
```json
{ "code": 200, "message": "success", "data": ... }
```

## 调用示例

新增预测：
```bash
curl -X POST http://localhost:8080/api/forecast \
  -H "Content-Type: application/json" \
  -d '{"itemCode":"11201A012","yearMonth":202607,"quantity":120,"deleteFlag":"分类1"}'
```

批量导入 BOM：
```bash
curl -X POST http://localhost:8080/api/bom/batch \
  -H "Content-Type: application/json" \
  -d '[{"parentCode":"11201A012","childCode":"21201A012","usageQty":1,"process":"aa","equipment":"aa001","moldCavity":1,"cycleTime":1,"staffCount":1,"taktTime":1}]'
```

触发计算并查询结果：
```bash
curl -X POST http://localhost:8080/api/production-plan/calculate
curl "http://localhost:8080/api/production-plan/by-period/202604"
```

## 项目结构
```
aps-system/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/aps/
    │   ├── ApsApplication.java
    │   ├── common/ApiResponse.java
    │   ├── exception/GlobalExceptionHandler.java
    │   ├── entity/         # 7 个实体
    │   ├── repository/     # 7 个 JpaRepository
    │   ├── service/        # 业务服务 + PlanCalculationService
    │   └── controller/     # REST 控制器
    └── resources/
        ├── application.yml
        └── schema.sql
```

## 注意事项
- BOM 展开内置循环引用检测（重复 itemCode 会被跳过并打日志）
- 计算前会清空 `t_production_plan`（全量重算）
- 报废率为 1.0（100%）会强制 planQty=0，避免除零
- 计划数量负值会被截断为 0
