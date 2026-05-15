# 能力测算报表改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 改造设备负荷与人员需求两个报表，使用真实计划数据按新公式计算，并在稼动天数页面新增全局"每天有效工时"配置。

**Architecture:** 纯前端方案，后端零改动。版本选择后并发请求 `/api/production-plan/by-version/{version}` 与 `/api/operating-days`，在 JS 中完成分组聚合。每天有效工时存入 `localStorage`（key: `aps.dailyEffectiveHours`），三个页面共享。

**Tech Stack:** Vanilla JS, HTML, Spring Boot 静态资源（已有后端 API）

---

## File Map

| 文件 | 动作 |
|---|---|
| `aps-system/src/main/resources/static/05-operating-days.html` | Modify — 新增每天有效工时配置区 |
| `aps-system/src/main/resources/static/11-equipment-load.html` | Modify — 替换工具栏和全部 JS |
| `aps-system/src/main/resources/static/10-workforce-report.html` | Modify — 替换工具栏和全部 JS |

---

### Task 1: 05-operating-days.html — 新增每天有效工时配置

**Files:**
- Modify: `aps-system/src/main/resources/static/05-operating-days.html:172-173`（HTML 插入）
- Modify: `aps-system/src/main/resources/static/05-operating-days.html`（script 区）

- [ ] **Step 1: 在 banner 与 filter-bar 之间插入配置区 HTML**

找到：
```html
</div>
<div class="filter-bar">
```
（banner 结束 + filter-bar 开始，位于约 172–173 行）

替换为：
```html
</div>
<div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:12px 18px;display:flex;align-items:center;gap:14px;margin-bottom:16px">
  <span style="font-size:12px;color:var(--color-neutral-600);font-weight:600;white-space:nowrap">每天有效工时（小时）</span>
  <input type="number" id="dailyHours" min="1" max="24" step="0.5"
    style="width:90px;padding:6px 10px;border:1px solid #d4dbe2;border-radius:6px;font-size:13px;font-family:var(--font-data)"
    oninput="saveDailyHours(this.value)">
  <span style="font-size:12px;color:var(--color-neutral-400)">设备负荷与人员需求报表共用此配置</span>
</div>
<div class="filter-bar">
```

- [ ] **Step 2: 在 script 区 `const API=location.origin;` 之后插入 localStorage 读写函数**

找到：
```js
const API=location.origin;
```

替换为：
```js
const API=location.origin;

function saveDailyHours(val) {
  const v = parseFloat(val);
  if (!isNaN(v) && v > 0) localStorage.setItem('aps.dailyEffectiveHours', String(v));
}
(function initDailyHours() {
  const saved = parseFloat(localStorage.getItem('aps.dailyEffectiveHours'));
  const inp = document.getElementById('dailyHours');
  if (inp) inp.value = isNaN(saved) ? 10.5 : saved;
  else {
    // 等 DOM ready 后再设（脚本在 body 末尾，直接设即可，此 else 仅保险）
    document.addEventListener('DOMContentLoaded', () => {
      const i = document.getElementById('dailyHours');
      if (i) i.value = isNaN(saved) ? 10.5 : saved;
    });
  }
})();
```

- [ ] **Step 3: 验证**

```bash
grep -n "dailyHours\|dailyEffectiveHours" /home/speedking/projects/APS/aps-system/src/main/resources/static/05-operating-days.html
```

预期：找到 `dailyHours`（input id）和 `dailyEffectiveHours`（localStorage key）。

- [ ] **Step 4: 提交**

```bash
cd /home/speedking/projects/APS
git add aps-system/src/main/resources/static/05-operating-days.html
git commit -m "feat: 稼动天数页面新增每天有效工时全局配置"
```

---

### Task 2: 11-equipment-load.html — 设备产能负荷报表

**Files:**
- Modify: `aps-system/src/main/resources/static/11-equipment-load.html:180-201`（工具栏）
- Modify: `aps-system/src/main/resources/static/11-equipment-load.html:222-377`（script 区）

- [ ] **Step 1: 替换工具栏内容**

找到整段工具栏：
```html
    <div class="toolbar">
      <div class="field">
        <label>工序</label>
        <select id="fProc"><option value="">全部</option><option>注塑</option><option>装配</option><option>焊接</option><option>检验</option><option>冲压</option><option>喷涂</option></select>
      </div>
      <div class="field">
        <label>期间</label>
        <input type="text" value="202601" style="width:88px"> <span style="color:var(--color-neutral-400)">—</span> <input type="text" value="202612" style="width:88px">
      </div>
      <div class="field">
        <label>仅超负</label>
        <input type="checkbox" id="warnOnly" onchange="render()" style="width:auto;accent-color:var(--color-primary)">
      </div>
      <div class="spacer"></div>
      <div class="legend">
        <span><span class="legend-dot" style="background:#4caf50"></span>&lt;60%</span>
        <span><span class="legend-dot" style="background:#0057b8"></span>60~80%</span>
        <span><span class="legend-dot" style="background:#fbc02d"></span>80~100%</span>
        <span><span class="legend-dot" style="background:#f44339"></span>&gt;100%</span>
      </div>
      <button class="btn btn-primary" onclick="render()">↻ 刷新</button>
    </div>
```

替换为：
```html
    <div class="toolbar">
      <div class="field">
        <label>计划版本</label>
        <select id="versionSel" style="min-width:180px" onchange="loadData()">
          <option value="">请选择版本</option>
        </select>
      </div>
      <div class="field">
        <label>工序</label>
        <select id="fProc" onchange="render()"><option value="">全部</option></select>
      </div>
      <div class="field">
        <label>仅超负</label>
        <input type="checkbox" id="warnOnly" onchange="render()" style="width:auto;accent-color:var(--color-primary)">
      </div>
      <div class="spacer"></div>
      <div class="legend">
        <span><span class="legend-dot" style="background:#4caf50"></span>&lt;60%</span>
        <span><span class="legend-dot" style="background:#0057b8"></span>60~85%</span>
        <span><span class="legend-dot" style="background:#fbc02d"></span>85~100%</span>
        <span><span class="legend-dot" style="background:#f44339"></span>&gt;100%</span>
      </div>
    </div>
```

- [ ] **Step 2: 替换整个 `<script>` 块**

找到（从 `<script>` 到 `</script>`，约 222–377 行，含完整 script 标签）：
```html
<script>
const EQ=[
```

一直到：
```html
loadEquipmentData();
</script>
```

替换为：
```html
<script>
const API = location.origin;
let planData = [], odData = [];

function getDailyHours() {
  return parseFloat(localStorage.getItem('aps.dailyEffectiveHours') || '10.5');
}

async function loadVersions() {
  try {
    const r = await fetch(`${API}/api/production-plan/versions`);
    const j = await r.json();
    const versions = j.data || [];
    const sel = document.getElementById('versionSel');
    if (!sel) return;
    sel.innerHTML = '<option value="">请选择版本</option>' +
      versions.map(v => `<option value="${v}">${v}</option>`).join('');
  } catch(e) { console.warn('loadVersions failed', e); }
}

async function loadData() {
  const sel = document.getElementById('versionSel');
  const version = sel?.value;
  const tbody = document.getElementById('dataTbody');
  if (!version) {
    if (tbody) tbody.innerHTML = '<tr><td colspan="20" style="text-align:center;padding:40px;color:var(--color-neutral-400)">请先选择计划版本</td></tr>';
    return;
  }
  try {
    const [planRes, odRes] = await Promise.all([
      fetch(`${API}/api/production-plan/by-version/${encodeURIComponent(version)}`).then(r => r.json()),
      fetch(`${API}/api/operating-days`).then(r => r.json()),
    ]);
    planData = planRes.data || [];
    odData   = odRes.data  || [];

    // 动态填充工序下拉
    const procs = [...new Set(planData.map(d => d.process).filter(Boolean))].sort();
    const fProc = document.getElementById('fProc');
    if (fProc) {
      const cur = fProc.value;
      fProc.innerHTML = '<option value="">全部</option>' + procs.map(p => `<option value="${p}">${p}</option>`).join('');
      if (procs.includes(cur)) fProc.value = cur;
    }

    render();
  } catch(e) {
    console.error('loadData failed', e);
    if (tbody) tbody.innerHTML = '<tr><td colspan="20" style="text-align:center;padding:40px;color:var(--color-error)">加载失败，请确认后端服务已启动</td></tr>';
  }
}

function render() {
  const dailyHours = getDailyHours();
  const warnOnly   = document.getElementById('warnOnly')?.checked;
  const procFilter = document.getElementById('fProc')?.value || '';

  // yearMonth → workDays
  const odMap = {};
  odData.forEach(d => { odMap[d.yearMonth] = d.workDays || 0; });

  // 所有期间（排序）
  const periods = [...new Set(planData.map(d => d.yearMonth))].sort();

  // 按 equipment+process 分组，累加 planQty*cycleTime/moldCavity（秒）
  const equipMap = {};
  planData.forEach(d => {
    if (procFilter && d.process !== procFilter) return;
    const mc = d.moldCavity || 1;
    const ct = d.cycleTime  || 0;
    const key = `${d.equipment}|||${d.process}|||${mc}|||${ct}`;
    if (!equipMap[key]) equipMap[key] = {};
    if (!equipMap[key][d.yearMonth]) equipMap[key][d.yearMonth] = { taskSec: 0 };
    equipMap[key][d.yearMonth].taskSec += (d.planQty || 0) * ct / mc;
  });

  function rateClass(r) {
    if (r > 1)    return 'lb-over';
    if (r >= .85) return 'lb-tight';
    if (r >= .60) return 'lb-normal';
    return 'lb-idle';
  }

  // 构建行数组
  const rows = Object.entries(equipMap).map(([key, byPeriod]) => {
    const [equipment, process, mc, ct] = key.split('|||');
    const periodRates = {};
    periods.forEach(ym => {
      const taskHours  = (byPeriod[ym]?.taskSec || 0) / 3600;
      const availHours = (odMap[ym] || 0) * dailyHours;
      periodRates[ym]  = { taskHours, availHours, rate: availHours > 0 ? taskHours / availHours : 0 };
    });
    const maxRate = Math.max(...Object.values(periodRates).map(v => v.rate));
    return { equipment, process, mc: parseInt(mc), ct: parseFloat(ct), periodRates, maxRate };
  }).filter(r => !warnOnly || r.maxRate > 1);

  // 更新表头
  const table = document.querySelector('.matrix-table');
  const thead = table?.querySelector('thead tr');
  if (thead) {
    thead.innerHTML =
      '<th class="sticky" style="min-width:140px">设备名称</th><th>工序</th><th>模腔</th><th>周期(s)</th>' +
      periods.map(p => `<th class="ph">${String(p).slice(0,4)}-${String(p).slice(4)}</th>`).join('') +
      '<th style="background:#e8f0fb;color:var(--color-primary);min-width:72px">峰值</th>';
  }

  // 更新统计卡片
  const allRates = rows.flatMap(r => Object.values(r.periodRates).map(v => v.rate));
  const avgRate  = allRates.length ? (allRates.reduce((a,b)=>a+b,0)/allRates.length*100).toFixed(1) : '—';
  const maxRateV = allRates.length ? (Math.max(...allRates)*100).toFixed(1) : '—';
  const overCnt  = rows.filter(r => r.maxRate > 1).length;
  const statVals = document.querySelectorAll('.stat-value');
  if (statVals[0]) statVals[0].innerHTML = `${rows.length}<span class="stat-unit">台</span>`;
  if (statVals[1]) statVals[1].innerHTML = `${avgRate}<span class="stat-unit">%</span>`;
  if (statVals[2]) statVals[2].innerHTML = `${maxRateV}<span class="stat-unit">%</span>`;
  if (statVals[3]) statVals[3].innerHTML = `${overCnt}<span class="stat-unit">次</span>`;

  // 渲染 tbody
  const tbody = document.getElementById('dataTbody');
  if (!tbody) return;
  if (rows.length === 0) {
    tbody.innerHTML = '<tr><td colspan="20" style="text-align:center;padding:40px;color:var(--color-neutral-400)">无匹配数据</td></tr>';
    return;
  }
  tbody.innerHTML = rows.map(row => {
    const cells = periods.map(ym => {
      const { taskHours, availHours, rate } = row.periodRates[ym];
      if (availHours === 0 && taskHours === 0) return '<td style="text-align:center">—</td>';
      const pct = (rate * 100).toFixed(1);
      return `<td><span class="lb ${rateClass(rate)}">${pct}%</span></td>`;
    }).join('');
    const peakPct = (row.maxRate * 100).toFixed(1);
    return `<tr>
      <td class="sticky"><strong>${row.equipment}</strong></td>
      <td>${row.process}</td>
      <td style="font-family:var(--font-data);text-align:center">${row.mc}</td>
      <td style="font-family:var(--font-data);text-align:center">${row.ct}</td>
      ${cells}
      <td style="background:#e8f0fb;text-align:center"><span class="lb ${rateClass(row.maxRate)}">${peakPct}%</span></td>
    </tr>`;
  }).join('');
}

loadVersions();
</script>
```

- [ ] **Step 3: 验证 JS 不含旧静态数组**

```bash
grep -n "const EQ\|const PER\|OPDAYS\|loadEquipmentData\|loadData_real" /home/speedking/projects/APS/aps-system/src/main/resources/static/11-equipment-load.html
```

预期：无输出。

```bash
grep -n "versionSel\|getDailyHours\|loadVersions\|loadData\|render" /home/speedking/projects/APS/aps-system/src/main/resources/static/11-equipment-load.html
```

预期：多行匹配。

- [ ] **Step 4: 提交**

```bash
cd /home/speedking/projects/APS
git add aps-system/src/main/resources/static/11-equipment-load.html
git commit -m "feat: 设备负荷页面改用新公式(cycleTime/moldCavity)，支持版本选择"
```

---

### Task 3: 10-workforce-report.html — 人员工时负荷报表

**Files:**
- Modify: `aps-system/src/main/resources/static/10-workforce-report.html:191-205`（工具栏）
- Modify: `aps-system/src/main/resources/static/10-workforce-report.html:316-402`（script 区）

- [ ] **Step 1: 替换工具栏内容**

找到：
```html
    <!-- filter -->
    <div class="toolbar">
      <div class="field"><label>期间范围</label>
        <select><option selected>202504 ~ 202506</option><option>202505 ~ 202507</option></select>
      </div>
      <div class="field"><label>工序</label>
        <select><option>全部</option><option>注塑 INJ</option><option>冲压 STM</option><option>机加 MCH</option><option>装配 ASM</option><option>检测 QC</option></select>
      </div>
      <div class="field"><label>设备</label>
        <select><option>全部</option><option>INJ-01</option><option>INJ-02</option><option>STM-A</option><option>MCH-01</option><option>ASM-L1</option></select>
      </div>
      <div class="spacer"></div>
      <button class="btn btn-ghost">重置</button>
      <button class="btn btn-primary">查询</button>
      <button class="btn btn-ghost">⬇ 导出</button>
    </div>
```

替换为：
```html
    <!-- filter -->
    <div class="toolbar">
      <div class="field">
        <label>计划版本</label>
        <select id="versionSel" style="min-width:180px" onchange="loadData()">
          <option value="">请选择版本</option>
        </select>
      </div>
      <div class="spacer"></div>
    </div>
```

- [ ] **Step 2: 替换整个 `<script>` 块**

找到（完整 script 标签，约 316–402 行）：
```html
<script>
document.querySelectorAll('.tab').forEach(t=>{
```

一直到：
```html
loadData();
</script>
```

替换为：
```html
<script>
// Tab switching
document.querySelectorAll('.tab').forEach(t => {
  t.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach(x => x.classList.remove('active'));
    t.classList.add('active');
    const id = t.dataset.tab;
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    document.getElementById(id).classList.add('active');
  });
});

const API = location.origin;
let planData = [], odData = [];

function getDailyHours() {
  return parseFloat(localStorage.getItem('aps.dailyEffectiveHours') || '10.5');
}

async function loadVersions() {
  try {
    const r = await fetch(`${API}/api/production-plan/versions`);
    const j = await r.json();
    const versions = j.data || [];
    const sel = document.getElementById('versionSel');
    if (!sel) return;
    sel.innerHTML = '<option value="">请选择版本</option>' +
      versions.map(v => `<option value="${v}">${v}</option>`).join('');
  } catch(e) { console.warn('loadVersions failed', e); }
}

async function loadData() {
  const sel = document.getElementById('versionSel');
  const version = sel?.value;
  if (!version) return;
  try {
    const [planRes, odRes] = await Promise.all([
      fetch(`${API}/api/production-plan/by-version/${encodeURIComponent(version)}`).then(r => r.json()),
      fetch(`${API}/api/operating-days`).then(r => r.json()),
    ]);
    planData = planRes.data || [];
    odData   = odRes.data  || [];
    renderProcessView();
    renderDetailView();
    updateStats();
  } catch(e) {
    console.error('loadData failed', e);
  }
}

function calcProcessData() {
  const dailyHours = getDailyHours();
  const odMap = {};
  odData.forEach(d => { odMap[d.yearMonth] = d.workDays || 0; });

  // 按 process × yearMonth 聚合人工时
  const grouped = {};
  planData.forEach(d => {
    const key = `${d.process}|||${d.yearMonth}`;
    if (!grouped[key]) grouped[key] = { process: d.process, yearMonth: d.yearMonth, laborHours: 0 };
    // 人工时(h) = planQty * staffCount * taktTime(s) / 3600
    grouped[key].laborHours += (d.planQty || 0) * (d.staffCount || 0) * (d.taktTime || 0) / 3600;
  });

  return Object.values(grouped).map(g => {
    const availHours = (odMap[g.yearMonth] || 0) * dailyHours;
    // 所需人数 = 总人工时 / 可用人时
    return { ...g, availHours, requiredPeople: availHours > 0 ? g.laborHours / availHours : 0 };
  });
}

function renderProcessView() {
  const tbody = document.getElementById('processTbody');
  if (!tbody) return;
  const pd = calcProcessData();
  if (pd.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px;color:var(--color-neutral-400)">暂无数据，请先执行计划计算</td></tr>';
    return;
  }
  const periods   = [...new Set(pd.map(d => d.yearMonth))].sort();
  const processes = [...new Set(pd.map(d => d.process))].sort();

  // 更新表头
  const thead = tbody.closest('table')?.querySelector('thead tr');
  if (thead) {
    thead.innerHTML = '<th>工序</th>' +
      periods.map(p => `<th class="num">${String(p).slice(0,4)}-${String(p).slice(4)}</th>`).join('') +
      '<th class="num">峰值</th>';
  }

  // 数据行
  const dataRows = processes.map(proc => {
    const vals = periods.map(ym => {
      const row = pd.find(d => d.process === proc && d.yearMonth === ym);
      return row ? row.requiredPeople : null;
    });
    const nums = vals.filter(v => v !== null);
    const peak = nums.length ? Math.max(...nums) : null;
    return `<tr>
      <td class="proc">${proc}</td>
      ${vals.map(v => `<td class="num">${v !== null ? v.toFixed(1) : '—'}</td>`).join('')}
      <td class="num" style="font-weight:600;color:var(--color-primary)">${peak !== null ? peak.toFixed(1) : '—'}</td>
    </tr>`;
  }).join('');

  // 合计行
  const totals = periods.map(ym => pd.filter(d => d.yearMonth === ym).reduce((s,d) => s + d.requiredPeople, 0));
  const totalPeak = totals.length ? Math.max(...totals) : 0;
  const sumRow = `<tr class="summary">
    <td>合计</td>
    ${totals.map(v => `<td class="num">${v.toFixed(1)}</td>`).join('')}
    <td class="num">${totalPeak.toFixed(1)}</td>
  </tr>`;

  tbody.innerHTML = dataRows + sumRow;
}

function renderDetailView() {
  const tbody = document.getElementById('detailTbody');
  if (!tbody) return;
  if (planData.length === 0) {
    tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:32px;color:var(--color-neutral-400)">暂无数据</td></tr>';
    return;
  }
  const dailyHours = getDailyHours();
  const odMap = {};
  odData.forEach(d => { odMap[d.yearMonth] = d.workDays || 0; });

  // 更新表头（含正确列数）
  const thead = tbody.closest('table')?.querySelector('thead tr');
  if (thead) {
    thead.innerHTML = '<th>期间</th><th>工序</th><th>设备</th><th>关联完成品</th>' +
      '<th class="num">月计划数量</th><th class="num">单件节拍(s)</th><th class="num">持台人数</th><th class="num">所需人数</th>';
  }

  tbody.innerHTML = planData.map(d => {
    const laborH  = (d.planQty || 0) * (d.staffCount || 0) * (d.taktTime || 0) / 3600;
    const availH  = (odMap[d.yearMonth] || 0) * dailyHours;
    const people  = availH > 0 ? laborH / availH : 0;
    return `<tr>
      <td style="font-family:var(--font-data)">${d.yearMonth}</td>
      <td>${d.process || '—'}</td>
      <td style="font-family:var(--font-data)">${d.equipment || '—'}</td>
      <td>${d.finishedProductCode || '—'}</td>
      <td class="num">${(d.planQty || 0).toLocaleString()}</td>
      <td class="num">${d.taktTime ?? '—'}</td>
      <td class="num">${d.staffCount ?? '—'}</td>
      <td class="num">${people.toFixed(2)}</td>
    </tr>`;
  }).join('');
}

function updateStats() {
  const pd = calcProcessData();
  const processes = new Set(pd.map(d => d.process));
  const periods = [...new Set(pd.map(d => d.yearMonth))].sort();
  const totals = periods.map(ym => pd.filter(d => d.yearMonth === ym).reduce((s,d) => s + d.requiredPeople, 0));
  const maxTotal = totals.length ? Math.max(...totals) : 0;
  const peakIdx = totals.indexOf(maxTotal);
  const peakPeriod = peakIdx >= 0 ? periods[peakIdx] : '—';

  const statVals = document.querySelectorAll('.stat-value');
  if (statVals[0]) statVals[0].textContent = periods.length >= 2 ? `${periods[0]}~${periods[periods.length-1]}` : (periods[0] || '—');
  if (statVals[1]) statVals[1].textContent = processes.size;
  if (statVals[2]) statVals[2].textContent = peakPeriod;
  if (statVals[3]) statVals[3].textContent = maxTotal.toFixed(1);
}

loadVersions();
</script>
```

- [ ] **Step 3: 验证旧 API 引用已删除**

```bash
grep -n "workforce-report\|wfData\|renderProcessView\|renderDetailView" /home/speedking/projects/APS/aps-system/src/main/resources/static/10-workforce-report.html | grep -v "function "
```

预期：`/api/workforce-report` 不再出现，`renderProcessView`/`renderDetailView` 只作为函数定义出现。

```bash
grep -n "versionSel\|getDailyHours\|calcProcessData" /home/speedking/projects/APS/aps-system/src/main/resources/static/10-workforce-report.html
```

预期：多行匹配。

- [ ] **Step 4: 提交**

```bash
cd /home/speedking/projects/APS
git add aps-system/src/main/resources/static/10-workforce-report.html
git commit -m "feat: 人员需求页面改用新公式(planQty*staffCount*taktTime)，支持版本选择"
```

---

### Task 4: 重新打包并验证

**Files:** 无新文件，只需构建

- [ ] **Step 1: 停旧进程、重新打包**

```bash
lsof -ti :8080 | xargs kill -9 2>/dev/null || true
cd /home/speedking/projects/APS/aps-system
mvn clean package -DskipTests -q && echo "BUILD OK"
```

预期：最后一行输出 `BUILD OK`

- [ ] **Step 2: 启动服务**

```bash
nohup java -jar /home/speedking/projects/APS/aps-system/target/*.jar > /tmp/aps.log 2>&1 &
sleep 20
curl -s http://localhost:8080 -o /dev/null -w "%{http_code}"
```

预期：`200`

- [ ] **Step 3: 验证版本列表 API**

```bash
curl -s http://localhost:8080/api/production-plan/versions | python3 -c "import sys,json; j=json.load(sys.stdin); print('versions:', j.get('data'))"
```

预期：输出包含版本列表，如 `versions: ['20260331-1']`

- [ ] **Step 4: 验证稼动天数 API**

```bash
curl -s http://localhost:8080/api/operating-days | python3 -c "import sys,json; d=json.load(sys.stdin); rows=d.get('data',[]); print('count:', len(rows)); print('sample:', rows[0] if rows else 'empty')"
```

预期：count > 0，sample 包含 yearMonth 和 workDays 字段

---

## Self-Review

| 需求 | 对应 Task |
|---|---|
| 每天有效工时可配置，存 localStorage | Task 1 |
| 设备负荷：作息时间 = planQty×cycleTime÷moldCavity÷3600 | Task 2 Step 2 |
| 设备负荷：可用时间 = operatingDays × dailyEffectiveHours | Task 2 Step 2 |
| 设备负荷：版本选择器 | Task 2 Step 1+2 |
| 人员负荷：总人工时 = planQty×staffCount×taktTime÷3600 | Task 3 Step 2 |
| 人员负荷：所需人数 = 总人工时 ÷ (operatingDays × dailyEffectiveHours) | Task 3 Step 2 |
| 人员负荷：按工序汇总+合计行 | Task 3 Step 2 |
| 人员负荷：明细列表含所需人数 | Task 3 Step 2 |
| 后端零改动 | ✓ 只改前端 3 个文件 |

Placeholder scan: 无 TBD / TODO，所有代码块均为完整可执行内容。

Type consistency: `versionSel`、`getDailyHours`、`loadVersions`、`loadData`、`render` 在 11-equipment-load.html 全程一致；`versionSel`、`getDailyHours`、`calcProcessData`、`loadVersions`、`loadData` 在 10-workforce-report.html 全程一致。
