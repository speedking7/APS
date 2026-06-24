# BOM Manufacturing Attributes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add required `manufacturingDepartment` and `manufacturingUnit` fields to BOM, propagate them into production plan results, and surface them across import/export and reporting views.

**Architecture:** Extend the existing BOM-as-process-source model by persisting the two new attributes on both `Bom` and `ProductionPlan`. Keep import and plan-calculation flows intact, adding validation and field propagation without changing planning, workforce, or equipment formulas.

**Tech Stack:** Java 11, Spring Boot 2.7, Spring Data JPA, JUnit 5, Mockito, Apache POI, static HTML/JS pages

---

### Task 1: Lock the New BOM Import Contract with Tests

**Files:**
- Modify: `aps-system/src/test/java/com/aps/service/ExcelImportServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add tests that build a BOM sheet with the new column order and assert:

```java
@Test
void importBom_missingManufacturingDepartment_isRejected() throws Exception {
    byte[] bytes = buildWorkbookWithBomRow(
            "P001", "C001", 1.0, "冲压", "EQ-01", null, "单元A",
            2, 30.0, 1.0, 15.0, 0.02, "v1");

    ImportResult result = service.importFromExcel(new ByteArrayInputStream(bytes));

    assertThat(result.getBomCount()).isEqualTo(0);
    assertThat(result.getErrors()).anyMatch(msg -> msg.contains("制造部门必填"));
}

@Test
void importBom_missingManufacturingUnit_isRejected() throws Exception {
    byte[] bytes = buildWorkbookWithBomRow(
            "P001", "C001", 1.0, "冲压", "EQ-01", "制造一部", null,
            2, 30.0, 1.0, 15.0, 0.02, "v1");

    ImportResult result = service.importFromExcel(new ByteArrayInputStream(bytes));

    assertThat(result.getBomCount()).isEqualTo(0);
    assertThat(result.getErrors()).anyMatch(msg -> msg.contains("制造单元必填"));
}

@Test
void importBom_savesManufacturingDepartmentAndUnit() throws Exception {
    byte[] bytes = buildWorkbookWithBomRow(
            "P001", "C001", 1.0, "冲压", "EQ-01", "制造一部", "单元A",
            2, 30.0, 1.0, 15.0, 0.02, "v1");

    service.importFromExcel(new ByteArrayInputStream(bytes));

    verify(bomRepository).saveAll(argThat(list -> list.size() == 1
            && "制造一部".equals(list.get(0).getManufacturingDepartment())
            && "单元A".equals(list.get(0).getManufacturingUnit())));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ExcelImportServiceTest test`
Expected: FAIL because `Bom` and `ExcelImportService` do not yet define or validate the new fields.

- [ ] **Step 3: Write minimal test helpers**

Update the workbook helper so the BOM row uses the final column order:

```java
private byte[] buildWorkbookWithBomRow(
        String parentCode, String childCode, Double usageQty, String process, String equipment,
        String manufacturingDepartment, String manufacturingUnit, Integer moldCavity,
        Double cycleTime, Double staffCount, Double taktTime, Double scrapRate, String version) {
    Workbook wb = new XSSFWorkbook();
    Sheet bom = wb.createSheet("BOM");
    bom.createRow(0);
    bom.createRow(1);
    Row row = bom.createRow(2);
    row.createCell(0).setCellValue(parentCode);
    if (childCode != null) row.createCell(1).setCellValue(childCode);
    if (usageQty != null) row.createCell(2).setCellValue(usageQty);
    if (process != null) row.createCell(3).setCellValue(process);
    if (equipment != null) row.createCell(4).setCellValue(equipment);
    if (manufacturingDepartment != null) row.createCell(5).setCellValue(manufacturingDepartment);
    if (manufacturingUnit != null) row.createCell(6).setCellValue(manufacturingUnit);
    if (moldCavity != null) row.createCell(7).setCellValue(moldCavity);
    if (cycleTime != null) row.createCell(8).setCellValue(cycleTime);
    if (staffCount != null) row.createCell(9).setCellValue(staffCount);
    if (taktTime != null) row.createCell(10).setCellValue(taktTime);
    if (scrapRate != null) row.createCell(11).setCellValue(scrapRate);
    if (version != null) row.createCell(12).setCellValue(version);
    ...
}
```

- [ ] **Step 4: Run test to verify it still fails for the right reason**

Run: `mvn -Dtest=ExcelImportServiceTest test`
Expected: FAIL with assertion failures about missing validation/field persistence, not broken workbook construction.

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/test/java/com/aps/service/ExcelImportServiceTest.java
git commit -m "test: lock bom manufacturing import contract"
```

### Task 2: Lock ProductionPlan Field Propagation with Tests

**Files:**
- Modify: `aps-system/src/test/java/com/aps/service/PlanCalculationServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add a test that verifies the plan result inherits the two new BOM fields:

```java
@Test
void testBomNode_manufacturingFieldsCopiedToResult() {
    String parent = "P001";
    String child = "C001";
    int period = 202601;
    String version = "202601";

    when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(parent));
    when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
    when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent, period, version))
            .thenReturn(Optional.of(new Demand(null, "AAA", parent, period, 100.0, null, null, 100.0, version)));
    when(inventoryCountRepository.findFirstByItemCodeAndVersion(child, version)).thenReturn(Optional.empty());
    when(bomRepository.findByParentCodeAndVersion(child, version)).thenReturn(Collections.emptyList());

    Bom childBomInfo = new Bom(null, child, null, 0.0, "CNC", "EQ-01",
            "制造一部", "单元A", 4, 30.0, 2.0, 15.0, null, version);
    when(bomRepository.findFirstByParentCodeAndVersion(child, version)).thenReturn(Optional.of(childBomInfo));

    Bom bomRel = new Bom(null, parent, child, 1.0, null, null,
            "制造一部", "单元A", null, null, null, null, null, version);
    when(bomRepository.findByParentCodeAndVersion(parent, version)).thenReturn(List.of(bomRel));

    service.calculate(request(version));

    ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
    verify(productionPlanRepository).saveAll(batchCaptor.capture());
    ProductionPlan childPlan = batchCaptor.getValue().get(1);

    assertThat(childPlan.getManufacturingDepartment()).isEqualTo("制造一部");
    assertThat(childPlan.getManufacturingUnit()).isEqualTo("单元A");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PlanCalculationServiceTest#testBomNode_manufacturingFieldsCopiedToResult test`
Expected: FAIL because `ProductionPlan` and `PlanCalculationService` do not yet carry the new fields.

- [ ] **Step 3: Keep existing constructor calls compiling**

Prepare to update existing `new Bom(...)` constructor invocations after entity shape changes so the test suite keeps expressing the intended scenarios.

- [ ] **Step 4: Re-run the focused test**

Run: `mvn -Dtest=PlanCalculationServiceTest test`
Expected: FAIL on the new propagation assertion until implementation lands.

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/test/java/com/aps/service/PlanCalculationServiceTest.java
git commit -m "test: lock manufacturing field propagation"
```

### Task 3: Implement Backend Model, Import, and Calculation Changes

**Files:**
- Modify: `aps-system/src/main/java/com/aps/entity/Bom.java`
- Modify: `aps-system/src/main/java/com/aps/entity/ProductionPlan.java`
- Modify: `aps-system/src/main/java/com/aps/service/BomService.java`
- Modify: `aps-system/src/main/java/com/aps/service/ExcelImportService.java`
- Modify: `aps-system/src/main/java/com/aps/service/PlanCalculationService.java`
- Modify: `aps-system/src/main/resources/schema.sql`
- Modify: `aps-system/src/main/resources/schema-current.sql`
- Modify: `aps-system/src/main/resources/data-seed.sql`

- [ ] **Step 1: Add the fields to `Bom`**

Update the entity with two required persisted columns:

```java
@Column(name = "manufacturing_department", length = 50, nullable = false)
private String manufacturingDepartment;

@Column(name = "manufacturing_unit", length = 50, nullable = false)
private String manufacturingUnit;
```

- [ ] **Step 2: Add the fields to `ProductionPlan`**

Use the same persisted field names so results snapshot the BOM state:

```java
@Column(name = "manufacturing_department", length = 50, nullable = false)
private String manufacturingDepartment;

@Column(name = "manufacturing_unit", length = 50, nullable = false)
private String manufacturingUnit;
```

- [ ] **Step 3: Update BOM update logic**

Extend `BomService.update(...)` to copy the two fields:

```java
existing.setManufacturingDepartment(entity.getManufacturingDepartment());
existing.setManufacturingUnit(entity.getManufacturingUnit());
```

- [ ] **Step 4: Implement BOM import parsing and validation**

Update `ExcelImportService.parseBoms(...)` for the new column indexes:

```java
String manufacturingDepartment = str(row, 5);
String manufacturingUnit = str(row, 6);
Double moldRaw = numOrNull(row, 7);
b.setCycleTime(numOrNull(row, 8));
b.setStaffCount(numOrNull(row, 9));
b.setTaktTime(numOrNull(row, 10));
Double scrapRate = numOrNull(row, 11);
String version = str(row, 12);

else if (manufacturingDepartment == null || manufacturingDepartment.isBlank())
    err = "制造部门必填";
else if (manufacturingUnit == null || manufacturingUnit.isBlank())
    err = "制造单元必填";

b.setManufacturingDepartment(manufacturingDepartment);
b.setManufacturingUnit(manufacturingUnit);
```

- [ ] **Step 5: Propagate the fields through plan calculation**

Extend the BOM extraction and `saveRecord(...)` signature:

```java
String manufacturingDepartment = null;
String manufacturingUnit = null;

if (bomOpt.isPresent()) {
    Bom b = bomOpt.get();
    manufacturingDepartment = b.getManufacturingDepartment();
    manufacturingUnit = b.getManufacturingUnit();
}

saveRecord(batch, finishedProduct, itemCode, period,
        process, equipment, manufacturingDepartment, manufacturingUnit, moldCavity,
        cycleTime, staffCount, taktTime, currentInventory, grossDemand,
        safetyDaysRecorded, scrapRate, planQty, resultVersion);
```

and in `saveRecord(...)`:

```java
plan.setManufacturingDepartment(manufacturingDepartment);
plan.setManufacturingUnit(manufacturingUnit);
```

- [ ] **Step 6: Update seed SQL**

Insert the two new required columns everywhere BOM seed rows are defined, for example:

```sql
INSERT IGNORE INTO t_bom (
  parent_code, child_code, usage_qty, process, equipment,
  manufacturing_department, manufacturing_unit,
  mold_cavity, cycle_time, staff_count, takt_time, scrap_rate, version
) VALUES
('11201A012', '11201A012-1', 1, 'aa', 'aa001', '制造一部', '单元A', 1, 30, 1, 30, 0.01, '20260331-1');
```

- [ ] **Step 7: Run tests to verify green**

Run:
`mvn -Dtest=ExcelImportServiceTest,PlanCalculationServiceTest test`

Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add aps-system/src/main/java/com/aps/entity/Bom.java aps-system/src/main/java/com/aps/entity/ProductionPlan.java aps-system/src/main/java/com/aps/service/BomService.java aps-system/src/main/java/com/aps/service/ExcelImportService.java aps-system/src/main/java/com/aps/service/PlanCalculationService.java aps-system/src/main/resources/schema.sql aps-system/src/main/resources/schema-current.sql aps-system/src/main/resources/data-seed.sql
git commit -m "feat: add manufacturing fields to bom and plans"
```

### Task 4: Update BOM and Result Views

**Files:**
- Modify: `aps-system/src/main/resources/static/03-bom-list.html`
- Modify: `aps-system/src/main/resources/static/08-plan-result.html`

- [ ] **Step 1: Update the BOM management page**

Add the two columns to the table and the two required inputs to the modal payload:

```javascript
document.getElementById('bomManufacturingDepartment').value = d ? (d.manufacturingDepartment || '') : '';
document.getElementById('bomManufacturingUnit').value = d ? (d.manufacturingUnit || '') : '';

const manufacturingDepartment = document.getElementById('bomManufacturingDepartment').value.trim();
const manufacturingUnit = document.getElementById('bomManufacturingUnit').value.trim();
if (!manufacturingDepartment) { showToast('制造部门不能为空'); return; }
if (!manufacturingUnit) { showToast('制造单元不能为空'); return; }

const body = {
  ...,
  manufacturingDepartment,
  manufacturingUnit
};
```

Update the import hint string to:

```html
Sheet 名称：<b>BOM</b>，列顺序：父零件/子零件/用量/工序/设备/制造部门/制造单元/模腔数/制造周期/持台人数/节拍/报废率/版本号
```

- [ ] **Step 2: Update the plan result page**

Show the new fields in the table and detail card:

```html
<th>制造部门</th>
<th>制造单元</th>
```

```javascript
<td>${d.manufacturingDepartment || '—'}</td>
<td>${d.manufacturingUnit || '—'}</td>
```

and in the modal summary:

```javascript
<div><span style="color:var(--color-neutral-400)">制造部门/单元</span><br><b>${d.manufacturingDepartment || '—'} / ${d.manufacturingUnit || '—'}</b></div>
```

- [ ] **Step 3: Run a focused build verification**

Run: `mvn -Dtest=ExcelImportControllerTest test`
Expected: PASS, confirming the API surface still serializes correctly.

- [ ] **Step 4: Commit**

```bash
git add aps-system/src/main/resources/static/03-bom-list.html aps-system/src/main/resources/static/08-plan-result.html
git commit -m "feat: surface manufacturing fields in bom and plan views"
```

### Task 5: Update Workforce and Equipment Reports

**Files:**
- Modify: `aps-system/src/main/resources/static/10-workforce-report.html`
- Modify: `aps-system/src/main/resources/static/11-equipment-load.html`

- [ ] **Step 1: Add the fields to the workforce detail view**

Extend the detail table with two columns and render from `ProductionPlan`:

```javascript
thead.innerHTML = '<th>期间</th><th>工序</th><th>设备</th><th>制造部门</th><th>制造单元</th><th>关联完成品</th>' +
  '<th class="num">月计划数量</th><th class="num">单件节拍(s)</th><th class="num">持台人数</th><th class="num">所需人数</th>';
```

```javascript
<td>${d.manufacturingDepartment || '—'}</td>
<td>${d.manufacturingUnit || '—'}</td>
```

- [ ] **Step 2: Add the fields to the equipment load view**

Show department/unit alongside equipment/process without changing grouping logic:

```javascript
const dept = d.manufacturingDepartment || '—';
const unit = d.manufacturingUnit || '—';
const key = `${d.equipment}|||${d.process}|||${dept}|||${unit}|||${mc}|||${ct}`;
```

Update the table header and row rendering:

```javascript
'<th>工序</th><th>制造部门</th><th>制造单元</th><th>模腔</th><th>周期(s)</th>'
```

```javascript
<td>${row.manufacturingDepartment}</td>
<td>${row.manufacturingUnit}</td>
```

- [ ] **Step 3: Run the service regression tests**

Run: `mvn -Dtest=PlanCalculationServiceTest test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add aps-system/src/main/resources/static/10-workforce-report.html aps-system/src/main/resources/static/11-equipment-load.html
git commit -m "feat: show manufacturing fields in capacity reports"
```

### Task 6: Final Verification

**Files:**
- Modify: `template-bom.xlsx`
- Modify: `docs/superpowers/specs/2026-05-24-bom-manufacturing-attrs-design.md` (only if implementation drift requires clarification)

- [ ] **Step 1: Update the Excel template artifact**

Ensure the workbook header order matches the implemented parser and UI guidance.

- [ ] **Step 2: Run the targeted test suite**

Run:
`mvn -Dtest=ExcelImportServiceTest,ExcelImportControllerTest,PlanCalculationServiceTest test`

Expected: PASS

- [ ] **Step 3: Run a broader package verification if fast enough**

Run:
`mvn test`

Expected: PASS, or capture the exact failing pre-existing tests if unrelated.

- [ ] **Step 4: Inspect the final diff**

Run:
`git diff --stat`

Expected: only the planned backend, static page, template, and spec/plan files changed.

- [ ] **Step 5: Commit**

```bash
git add template-bom.xlsx docs/superpowers/plans/2026-05-24-bom-manufacturing-attrs.md
git commit -m "chore: finalize bom manufacturing attribute rollout"
```
