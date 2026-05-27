# BOM Seed Master Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate repeatable test master data from the current BOM, clear the target master-data tables, write the generated dataset into MySQL, and export matching SQL/XLSX artifacts.

**Architecture:** Add a standalone Python generator under `scripts/` that reads BOM rows from MySQL through WSL, derives five datasets with deterministic rules, writes an `.sql` artifact, writes an `.xlsx` artifact matching the app import formats, and optionally applies the SQL back into MySQL. Keep the generation logic pure enough to cover with a small `unittest` suite.

**Tech Stack:** Python 3, `openpyxl`, WSL `mysql`, PowerShell

---

### Task 1: Lock generation rules with tests

**Files:**
- Create: `scripts/test_generate_bom_seed_data.py`

- [ ] **Step 1: Write the failing test**

```python
import unittest

from generate_bom_seed_data import build_seed_bundle


class SeedGeneratorTests(unittest.TestCase):
    def test_build_seed_bundle_generates_expected_scopes(self):
        bom_rows = [
            {
                "parent_code": "FP100",
                "child_code": "FP100-1",
                "usage_qty": 1.0,
                "process": "焊接",
                "equipment": "EQ-01",
                "manufacturing_department": "制造一课",
                "manufacturing_unit": "焊接",
                "version": "V1",
            },
            {
                "parent_code": "FP100-1",
                "child_code": "SEMI200",
                "usage_qty": 1.0,
                "process": "整理",
                "equipment": "",
                "manufacturing_department": "制造二课",
                "manufacturing_unit": "组立",
                "version": "V1",
            },
        ]

        bundle = build_seed_bundle(bom_rows, [202606, 202607, 202608], "V1")

        self.assertEqual(["FP100"], [row["item_code"] for row in bundle["demand_rows"][0:1]])
        self.assertIn("FP100-1", {row["part_no"] for row in bundle["part_rows"]})
        self.assertIn("SEMI200", {row["item_code"] for row in bundle["safety_rows"]})
        self.assertNotIn("FP100-1", {row["item_code"] for row in bundle["safety_rows"]})
        self.assertEqual(1, len(bundle["equipment_rows"]))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest scripts.test_generate_bom_seed_data -v`
Expected: FAIL because `generate_bom_seed_data` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
def build_seed_bundle(bom_rows, months, version):
    return {
        "demand_rows": [],
        "part_rows": [],
        "equipment_rows": [],
        "safety_rows": [],
        "inventory_rows": [],
    }
```

- [ ] **Step 4: Run test to verify it passes after full implementation**

Run: `python -m unittest scripts.test_generate_bom_seed_data -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add scripts/test_generate_bom_seed_data.py scripts/generate_bom_seed_data.py
git commit -m "feat: add BOM seed data generator"
```

### Task 2: Implement the repeatable generator

**Files:**
- Create: `scripts/generate_bom_seed_data.py`

- [ ] **Step 1: Load BOM rows from MySQL through WSL**

```python
def load_bom_rows(mysql_port: int) -> list[dict[str, object]]:
    sql = """
    SELECT parent_code, child_code, usage_qty, process, equipment,
           manufacturing_department, manufacturing_unit, version
    FROM t_bom
    ORDER BY parent_code, child_code
    """
```

- [ ] **Step 2: Build deterministic datasets**

```python
def build_seed_bundle(bom_rows, months, version):
    # roots: parent codes that are not real children of another parent
    # semi-finished: parent codes that are also children, excluding *-1
    # part master: every distinct code including *-1
    # equipment: distinct department + equipment
```

- [ ] **Step 3: Emit SQL and Excel artifacts from the same in-memory bundle**

```python
def write_sql(bundle, output_path): ...
def write_workbook(bundle, output_path): ...
```

- [ ] **Step 4: Add apply mode that clears and reseeds the five target tables**

```python
TARGET_TABLES = [
    "t_demand",
    "t_part_master",
    "t_equipment_catalog",
    "t_safety_stock",
    "t_inventory_count",
]
```

- [ ] **Step 5: Run the generator end-to-end**

Run: `python scripts/generate_bom_seed_data.py --start-month 202606 --months 3 --apply`
Expected: exit 0 and artifacts written under `docs/superpowers/generated/`

### Task 3: Verify database state and exported artifacts

**Files:**
- Modify: `docs/superpowers/generated/2026-05-27-bom-seed-master-data.sql`
- Modify: `docs/superpowers/generated/2026-05-27-bom-seed-master-data.xlsx`

- [ ] **Step 1: Check target table row counts**

Run:

```bash
@'
SELECT COUNT(*) AS demand_rows FROM t_demand;
SELECT COUNT(*) AS part_rows FROM t_part_master;
SELECT COUNT(*) AS equipment_rows FROM t_equipment_catalog;
SELECT COUNT(*) AS safety_rows FROM t_safety_stock;
SELECT COUNT(*) AS inventory_rows FROM t_inventory_count;
'@ | wsl bash -lc "mysql -uroot -proot -h 127.0.0.1 -P 3307 -D aps_db -t"
```

Expected: non-zero counts for all five tables.

- [ ] **Step 2: Check demand month coverage**

Run:

```bash
@'
SELECT year_month, COUNT(*) AS rows
FROM t_demand
GROUP BY year_month
ORDER BY year_month;
'@ | wsl bash -lc "mysql -uroot -proot -h 127.0.0.1 -P 3307 -D aps_db -t"
```

Expected: exactly `202606`, `202607`, `202608`.

- [ ] **Step 3: Check sample scope rules**

Run:

```bash
@'
SELECT COUNT(*) AS virtual_part_rows FROM t_part_master WHERE part_no LIKE '%-1';
SELECT COUNT(*) AS virtual_safety_rows FROM t_safety_stock WHERE item_code LIKE '%-1';
'@ | wsl bash -lc "mysql -uroot -proot -h 127.0.0.1 -P 3307 -D aps_db -t"
```

Expected: `virtual_part_rows > 0` and `virtual_safety_rows = 0`.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/generated scripts/generate_bom_seed_data.py scripts/test_generate_bom_seed_data.py docs/superpowers/plans/2026-05-27-bom-seed-master-data.md
git commit -m "feat: seed master data from BOM"
```
