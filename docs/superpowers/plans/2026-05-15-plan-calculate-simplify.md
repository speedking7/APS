# 计划计算页面简化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 简化 07-plan-calculate.html 的参数区：移除计划期间和计算模式，将4个分散版本输入合并为一个动态下拉选择框，结果版本默认跟随主数据版本且可手工改。

**Architecture:** 纯前端修改，只改 `07-plan-calculate.html`。后端 `CalculateRequest` 已有 `version`（主数据版本）和 `resultVersion`（结果版本）字段，无需改动。版本列表通过并发请求5个主数据API，取所有 `version` 字段的并集后填充 `<select>`。

**Tech Stack:** Vanilla JS, HTML, Spring Boot（已有后端 API）

---

## File Map

| 文件 | 动作 |
|---|---|
| `aps-system/src/main/resources/static/07-plan-calculate.html` | Modify — 唯一改动文件 |

---

### Task 1: 移除"计划期间"和"计算模式"两个 field-row

**Files:**
- Modify: `aps-system/src/main/resources/static/07-plan-calculate.html:177-219`

- [ ] **Step 1: 删除"计划期间" field-row**

找到并删除下面整段 HTML（约 177–189 行）：

```html
          <div class="field-row">
            <label>计划期间</label>
            <div class="dual">
              <div class="dual-cell">
                <label>起始期间</label>
                <select><option selected>202504</option><option>202505</option><option>202506</option></select>
              </div>
              <div class="dual-cell">
                <label>截止期间</label>
                <select><option>202504</option><option>202505</option><option selected>202506</option></select>
              </div>
            </div>
          </div>
```

- [ ] **Step 2: 删除"计算模式" field-row**

找到并删除下面整段 HTML（约 207–219 行）：

```html
          <div class="field-row">
            <label>计算模式</label>
            <div class="radio-group">
              <label class="radio-opt sel">
                <input type="radio" name="mode" checked>
                <div><div class="rl">全量重算</div><div class="rd">清空本期已有结果后重新计算 · 推荐月度首次执行</div></div>
              </label>
              <label class="radio-opt">
                <input type="radio" name="mode">
                <div><div class="rl">增量计算</div><div class="rd">仅计算有变更的物料 · 适用于盘点数或预测局部更新</div></div>
              </label>
            </div>
          </div>
```

- [ ] **Step 3: 验证页面结构只剩"完成品范围"、"版本参数"、"结果版本"三个 field-row**

```bash
grep -n "field-row\|计划期间\|计算模式" aps-system/src/main/resources/static/07-plan-calculate.html
```

预期：输出中不含"计划期间"、"计算模式"字样。

---

### Task 2: 将4个版本输入合并为一个动态下拉

**Files:**
- Modify: `aps-system/src/main/resources/static/07-plan-calculate.html:221-246`

- [ ] **Step 1: 替换"版本参数" field-row**

将以下旧 HTML（4个 text input）：

```html
          <div class="field-row">
            <label>版本参数</label>
            <div class="dual" style="grid-template-columns:1fr 1fr;gap:10px">
              <div class="dual-cell">
                <label>需求版本</label>
                <input type="text" id="demandVersion" placeholder="如 v1">
              </div>
              <div class="dual-cell">
                <label>BOM 版本</label>
                <input type="text" id="bomVersion" placeholder="如 v1">
              </div>
              <div class="dual-cell">
                <label>安全库存版本</label>
                <input type="text" id="safetyStockVersion" placeholder="如 v1">
              </div>
              <div class="dual-cell">
                <label>盘点数版本</label>
                <input type="text" id="inventoryVersion" placeholder="如 v1">
              </div>
            </div>
          </div>
```

替换为：

```html
          <div class="field-row">
            <label>主数据版本</label>
            <select id="baseVersion" style="width:100%">
              <option value="">加载中…</option>
            </select>
            <div style="margin-top:6px;font-size:11px;color:var(--color-neutral-400)">
              版本列表从入库需求数、BOM、安全库存、稼动天数、盘点数中动态加载
            </div>
          </div>
```

- [ ] **Step 2: 更新"结果版本" field-row — 保留 input，绑定 onchange**

将旧的结果版本 field-row：

```html
          <div class="field-row">
            <label>结果版本（标识本次输出）</label>
            <input type="text" id="resultVersion" value="2026-05 正式版" placeholder="将作为本次计算结果的版本标签">
          </div>
```

替换为：

```html
          <div class="field-row">
            <label>结果版本<span style="font-size:11px;color:var(--color-neutral-400);margin-left:6px">（默认同主数据版本，可手工修改）</span></label>
            <input type="text" id="resultVersion" placeholder="将作为本次计算结果的版本标签">
          </div>
```

- [ ] **Step 3: 验证 HTML 结构正确**

```bash
grep -n "baseVersion\|resultVersion\|demandVersion\|bomVersion" aps-system/src/main/resources/static/07-plan-calculate.html
```

预期：`baseVersion` 和 `resultVersion` 出现，`demandVersion`/`bomVersion`/`safetyStockVersion`/`inventoryVersion` 不再出现。

---

### Task 3: 更新 JS — 版本加载、联动、runCalc

**Files:**
- Modify: `aps-system/src/main/resources/static/07-plan-calculate.html`（script 区）

- [ ] **Step 1: 在 `checkReadiness` 函数中，并发加载版本列表并填充 select**

找到 `checkReadiness` 函数中 `const checks = [` 之前的位置，在获取到5个 API 响应后，添加版本并集填充逻辑。

将 `checkReadiness` 函数中现有的如下部分：

```js
    const getVersions = (arr) => {
      const vs = [...new Set((arr||[]).map(d=>d.version).filter(Boolean))].sort();
      return vs.length ? vs.join(' / ') : '—';
    };

    const checks = [
```

替换为：

```js
    const getVersions = (arr) => {
      const vs = [...new Set((arr||[]).map(d=>d.version).filter(Boolean))].sort();
      return vs.length ? vs.join(' / ') : '—';
    };

    // 填充主数据版本下拉（取5个数据源版本的并集）
    const allVersions = [...new Set([
      ...(dem.data||[]).map(d=>d.version),
      ...(bom.data||[]).map(d=>d.version),
      ...(ss.data||[]).map(d=>d.version),
      ...(od.data||[]).map(d=>d.version),
      ...(ic.data||[]).map(d=>d.version),
    ].filter(Boolean))].sort();
    const vSel = document.getElementById('baseVersion');
    if (vSel) {
      const prev = vSel.value;
      vSel.innerHTML = '<option value="">请选择版本</option>' +
        allVersions.map(v => `<option value="${v}">${v}</option>`).join('');
      // 恢复之前的选择（如果还存在）
      if (allVersions.includes(prev)) vSel.value = prev;
      // 联动：baseVersion 变化时更新 resultVersion（若 resultVersion 未被手工修改）
      vSel.onchange = () => syncResultVersion();
      // 初次加载时同步
      syncResultVersion();
    }

    const checks = [
```

- [ ] **Step 2: 添加 `syncResultVersion` 辅助函数（放在 `checkReadiness` 函数之前）**

在 `const API = location.origin;` 之后，`checkReadiness` 函数之前，插入：

```js
// baseVersion 变化时，若 resultVersion 与旧 baseVersion 相同或为空则自动同步
let _lastAutoVersion = '';
function syncResultVersion() {
  const bv = (document.getElementById('baseVersion')?.value || '').trim();
  const rv = document.getElementById('resultVersion');
  if (!rv) return;
  // 只有 resultVersion 是空或等于上次自动填入的值时才自动同步
  if (!rv.value || rv.value === _lastAutoVersion) {
    rv.value = bv;
    _lastAutoVersion = bv;
  }
}
```

- [ ] **Step 3: 更新 `runCalc` — 从 `baseVersion` 读取 version**

找到 runCalc 中构造 `calcReq` 的代码：

```js
    // 取第一个非空版本号作为统一版本（后端已合并为单一 version 字段）
    const demVer = document.getElementById('demandVersion')?.value?.trim() || null;
    const bomVer = document.getElementById('bomVersion')?.value?.trim() || null;
    const ssVer  = document.getElementById('safetyStockVersion')?.value?.trim() || null;
    const invVer = document.getElementById('inventoryVersion')?.value?.trim() || null;
    const calcReq = {
      version:       demVer || bomVer || ssVer || invVer || null,
      resultVersion: document.getElementById('resultVersion')?.value?.trim() || null,
    };
```

替换为：

```js
    const version       = document.getElementById('baseVersion')?.value?.trim() || null;
    const resultVersion = document.getElementById('resultVersion')?.value?.trim() || null;
    if (!version) {
      btn.classList.remove('loading'); btn.disabled = false; txt.textContent = '▶ 执行计算';
      progArea.classList.remove('show');
      alert('请先选择主数据版本');
      return;
    }
    const calcReq = { version, resultVersion };
```

- [ ] **Step 4: 验证 JS 不再引用旧的4个 input id**

```bash
grep -n "demandVersion\|bomVersion\|safetyStockVersion\|inventoryVersion" aps-system/src/main/resources/static/07-plan-calculate.html
```

预期：无输出。

---

### Task 4: 构建并启动服务，手工验证

**Files:** 无新文件，只需构建

- [ ] **Step 1: 终止旧进程并重新打包**

```bash
cd aps-system
lsof -ti :8080 | xargs kill -9 2>/dev/null
mvn clean package -DskipTests -q
echo "BUILD OK"
```

预期：最后一行输出 `BUILD OK`

- [ ] **Step 2: 启动服务**

```bash
nohup java -jar target/*.jar > /tmp/aps.log 2>&1 &
sleep 20
curl -s http://localhost:8080 -o /dev/null -w "%{http_code}"
```

预期：输出 `200`

- [ ] **Step 3: 验证版本下拉能加载**

```bash
curl -s http://localhost:8080/api/demand | python3 -c "import sys,json; d=json.load(sys.stdin); print([x.get('version') for x in (d.get('data') or [])])"
```

预期：输出包含 `'20260331-1'` 的列表

- [ ] **Step 4: 提交**

```bash
git add aps-system/src/main/resources/static/07-plan-calculate.html
git commit -m "feat: 简化计划计算参数，版本合并为单一下拉，结果版本自动联动"
```

---

## Self-Review

| 需求 | 对应 Task |
|---|---|
| 移除"计划期间" | Task 1 Step 1 |
| 移除"计算模式" | Task 1 Step 2 |
| 版本参数合并为一个下拉，选项动态加载 | Task 2 Step 1 + Task 3 Step 1 |
| 结果版本默认同主数据版本 | Task 2 Step 2 + Task 3 Step 2 |
| 结果版本可手工改 | Task 3 Step 2（`syncResultVersion` 只在未手动修改时自动填充）|
| 相同版本全删全导 | 后端已支持，前端传 `version` + `resultVersion` — Task 3 Step 3 |
| 无后端改动 | ✓ CalculateRequest 已有两个字段 |

Placeholder scan: 无 TBD / TODO / "implement later"。所有代码块均为完整可执行内容。

Type consistency: `baseVersion`（select id）、`resultVersion`（input id）、`_lastAutoVersion`（module-level let）在所有 task 中命名一致。
