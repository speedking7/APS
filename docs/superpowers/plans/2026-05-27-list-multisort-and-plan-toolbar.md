# List Multi-Sort And Plan Toolbar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the redundant plan-result toolbar actions and add a shared multi-column cascade sort interaction across all static list/matrix pages.

**Architecture:** Add one reusable browser-side sorting helper in `static/`, then wire each list page to declare sortable columns and render sort state in the table header. Keep all sorting client-side so no backend contract changes are required.

**Tech Stack:** Static HTML, browser JavaScript, Python `unittest`

---

### Task 1: Lock the page-level contract with a failing test

**Files:**
- Create: `scripts/test_static_sort_integration.py`

- [ ] **Step 1: Write the failing test**

```python
import unittest
from pathlib import Path


class StaticSortIntegrationTests(unittest.TestCase):
    def test_plan_result_removes_toolbar_actions_and_loads_sort_helper(self):
        html = Path("aps-system/src/main/resources/static/08-plan-result.html").read_text(encoding="utf-8")
        self.assertNotIn("切换版本", html)
        self.assertNotIn("手工调整", html)
        self.assertIn("table-multisort.js", html)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest scripts.test_static_sort_integration -v`
Expected: FAIL because the helper file is not yet referenced and the buttons still exist.

- [ ] **Step 3: Write minimal implementation**

```python
# no production code here yet; this step exists to preserve red/green order
```

- [ ] **Step 4: Run test to verify it passes after implementation**

Run: `python -m unittest scripts.test_static_sort_integration -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add scripts/test_static_sort_integration.py aps-system/src/main/resources/static
git commit -m "feat: add shared list multi-sort interactions"
```

### Task 2: Add a shared multi-sort helper

**Files:**
- Create: `aps-system/src/main/resources/static/table-multisort.js`

- [ ] **Step 1: Add a small reusable helper**

```javascript
window.ApsTableMultiSort = {
  sortRows(rows, sortChain, columnDefs) { /* ... */ },
  toggleSort(sortChain, key) { /* ... */ },
  renderHeaderState(table, sortChain) { /* ... */ }
};
```

- [ ] **Step 2: Support three-state toggling per column**

```javascript
// click 1 => asc, click 2 => desc, click 3 => remove from chain
```

- [ ] **Step 3: Support mixed value types**

```javascript
// numeric, yearMonth, text, empty-last
```

- [ ] **Step 4: Verify the helper is syntactically valid**

Run: `node --check aps-system/src/main/resources/static/table-multisort.js`
Expected: exit 0

### Task 3: Wire all list pages to the helper

**Files:**
- Modify: `aps-system/src/main/resources/static/02-forecast-list.html`
- Modify: `aps-system/src/main/resources/static/03-bom-list.html`
- Modify: `aps-system/src/main/resources/static/04-material-params.html`
- Modify: `aps-system/src/main/resources/static/06-inventory-count.html`
- Modify: `aps-system/src/main/resources/static/08-plan-result.html`
- Modify: `aps-system/src/main/resources/static/10-workforce-report.html`
- Modify: `aps-system/src/main/resources/static/11-equipment-load.html`
- Modify: `aps-system/src/main/resources/static/12-equipment-catalog.html`
- Modify: `aps-system/src/main/resources/static/13-part-master.html`

- [ ] **Step 1: Load the helper script on each page**

```html
<script src="table-multisort.js"></script>
```

- [ ] **Step 2: Mark sortable headers with stable keys**

```html
<th data-sort-key="itemCode" data-sort-type="text">存货编码</th>
```

- [ ] **Step 3: Apply the current sort chain before row rendering**

```javascript
const sortedRows = ApsTableMultiSort.sortRows(filteredRows, sortChain, columnDefs);
```

- [ ] **Step 4: Re-render sort indicators on every render**

```javascript
ApsTableMultiSort.renderHeaderState(document.querySelector('.data-table'), sortChain);
```

- [ ] **Step 5: Remove the redundant buttons from plan result**

```html
<!-- remove: 切换版本 / 手工调整 -->
```

### Task 4: Verify static wiring and runtime behavior

**Files:**
- Test: `scripts/test_static_sort_integration.py`

- [ ] **Step 1: Run the static integration test**

Run: `python -m unittest scripts.test_static_sort_integration -v`
Expected: PASS

- [ ] **Step 2: Check the shared helper syntax**

Run: `node --check aps-system/src/main/resources/static/table-multisort.js`
Expected: exit 0

- [ ] **Step 3: Check all target pages reference the helper**

Run:

```bash
Select-String -Path aps-system\src\main\resources\static\02-forecast-list.html,aps-system\src\main\resources\static\03-bom-list.html,aps-system\src\main\resources\static\04-material-params.html,aps-system\src\main\resources\static\06-inventory-count.html,aps-system\src\main\resources\static\08-plan-result.html,aps-system\src\main\resources\static\10-workforce-report.html,aps-system\src\main\resources\static\11-equipment-load.html,aps-system\src\main\resources\static\12-equipment-catalog.html,aps-system\src\main\resources\static\13-part-master.html -Pattern 'table-multisort.js'
```

Expected: one hit per page.
