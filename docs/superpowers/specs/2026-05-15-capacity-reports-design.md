# 能力测算报表改造 Design

## Goal

改造两个能力测算页面，使用真实计划数据驱动，并在稼动天数页面增加全局每天有效工时配置。

## Architecture

纯前端方案，后端零改动。所有计算在浏览器 JS 中完成，复用现有两个 API：
- `GET /api/production-plan/by-version/{version}` — 返回生产计划结果列表
- `GET /api/production-plan/versions` — 返回所有版本列表
- `GET /api/operating-days` — 返回各月稼动天数

每天有效工时存入 `localStorage`（key: `aps.dailyEffectiveHours`，默认 10.5），由 05-operating-days.html 维护，两个报表页面读取。

## Tech Stack

Vanilla JS、HTML、Spring Boot 静态资源（已有后端）

---

## Feature 1：每天有效工时配置（05-operating-days.html）

在稼动天数页面顶部加一个全局参数区（panel），包含：
- 标签："每天有效工时（小时）"
- `<input type="number" id="dailyHours" value="10.5" min="1" max="24" step="0.5">`
- 实时 `oninput` 写入 `localStorage.setItem('aps.dailyEffectiveHours', value)`
- 页面加载时读取 localStorage 恢复值

---

## Feature 2：设备产能负荷（11-equipment-load.html）

### 交互

- 页面顶部：版本选择器（`<select id="versionSel">`），从 `/api/production-plan/versions` 动态加载
- 选版本后触发数据加载

### 数据流

1. 并发请求 `/api/production-plan/by-version/{version}` 和 `/api/operating-days`
2. 构建 `operatingDaysMap`：`{ yearMonth → workDays }`
3. 按 `(equipment, process, moldCavity, cycleTime, yearMonth)` 分组，累加 `planQty`
4. 每组计算：
   - `作息时间(h) = sum(planQty × cycleTime ÷ moldCavity) ÷ 3600`
   - `可用时间(h) = operatingDaysMap[yearMonth] × dailyEffectiveHours`
   - `负荷率 = 作息时间 ÷ 可用时间`

### 展示

- 表格：行 = 设备（含工序、模腔数、周期），列 = 各月份
- 单元格：负荷率百分比 + 颜色（< 60% 绿，60–85% 蓝，85–100% 黄，> 100% 红）
- 无数据月份显示"—"

---

## Feature 3：人员工时负荷（10-workforce-report.html）

### 交互

- 同样有版本选择器

### 数据流

1. 并发请求同上两个 API
2. 按 `(process, yearMonth)` 分组，累加 `planQty × staffCount × taktTime`
3. 每组计算：
   - `总人工时(h) = sum(planQty × staffCount × taktTime) ÷ 3600`
   - `可用时间(h) = operatingDaysMap[yearMonth] × dailyEffectiveHours`
   - `所需人数 = 总人工时 ÷ 可用时间`

### 展示

- 表格：行 = 工序，列 = 各月份
- 单元格：所需人数（保留 1 位小数）
- 颜色阈值可用编制人数参考（无编制数据时仅显示数值，不着色）

---

## File Map

| 文件 | 动作 |
|---|---|
| `static/05-operating-days.html` | 增加每天有效工时配置区 |
| `static/11-equipment-load.html` | 重写为设备产能负荷报表 |
| `static/10-workforce-report.html` | 重写为人员工时负荷报表 |

后端无改动。

---

## 关键公式汇总

| 报表 | 指标 | 公式 |
|---|---|---|
| 设备产能负荷 | 作息时间(h) | `sum(planQty × cycleTime ÷ moldCavity) ÷ 3600` |
| 设备产能负荷 | 可用时间(h) | `operatingDays × dailyEffectiveHours` |
| 设备产能负荷 | 负荷率 | `作息时间 ÷ 可用时间` |
| 人员工时负荷 | 总人工时(h) | `sum(planQty × staffCount × taktTime) ÷ 3600` |
| 人员工时负荷 | 所需人数 | `总人工时 ÷ (operatingDays × dailyEffectiveHours)` |

`dailyEffectiveHours` 从 `localStorage.getItem('aps.dailyEffectiveHours') || 10.5` 读取。
