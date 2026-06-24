# BOM Root Product Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `root_product_code` to BOM data so calculation only expands the BOM tree belonging to the current finished product.

**Architecture:** Store BOM rows as finished-product-scoped trees instead of a version-wide shared graph. Import, manual BOM editing, and calculation all become root-product-aware, while downstream analysis continues to aggregate from production-plan results rather than the BOM table.

**Tech Stack:** Spring Boot 2.7, Spring Data JPA, MySQL 8, static HTML/JS pages, Apache POI, JUnit 5, Mockito

---

### Task 1: Add BOM Root Product Field To Domain Model

**Files:**
- Modify: `aps-system/src/main/java/com/aps/entity/Bom.java`
- Modify: `aps-system/src/main/java/com/aps/repository/BomRepository.java`
- Test: `aps-system/src/test/java/com/aps/service/PlanCalculationServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add a test that provides BOM rows for two finished products sharing the same parent/child codes and asserts that calculation for `P001` only uses rows scoped to `P001`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=PlanCalculationServiceTest#testCalculation_usesOnlyBomRowsForMatchingRootProduct test`

Expected: FAIL because `Bom` has no `rootProductCode` field and repository/model cannot express this scope.

- [ ] **Step 3: Add the field and repository methods**

Update `Bom`:

```java
@Column(name = "root_product_code", length = 50, nullable = false)
private String rootProductCode;
```

Add repository methods:

```java
List<Bom> findByVersionAndRootProductCode(String version, String rootProductCode);
List<Bom> findByParentCodeAndVersionAndRootProductCode(String parentCode, String version, String rootProductCode);
Optional<Bom> findFirstByParentCodeAndVersionAndRootProductCode(String parentCode, String version, String rootProductCode);
```

- [ ] **Step 4: Run test to verify compilation passes and test still fails for behavior**

Run: `mvn -q -Dtest=PlanCalculationServiceTest#testCalculation_usesOnlyBomRowsForMatchingRootProduct test`

Expected: FAIL because calculation still loads version-wide BOM rows.

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/main/java/com/aps/entity/Bom.java aps-system/src/main/java/com/aps/repository/BomRepository.java aps-system/src/test/java/com/aps/service/PlanCalculationServiceTest.java
git commit -m "feat: add root product scope to bom model"
```

### Task 2: Scope BOM Import To Root Product

**Files:**
- Modify: `aps-system/src/main/java/com/aps/service/ExcelImportService.java`
- Modify: `aps-system/src/test/java/com/aps/service/ExcelImportServiceTest.java`
- Modify: `aps-system/src/main/resources/schema.sql`
- Modify: `aps-system/src/main/resources/schema-current.sql`

- [ ] **Step 1: Write the failing test**

Extend import tests so the BOM workbook includes a `root_product_code` column and assert the parsed `Bom` entity stores it.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ExcelImportServiceTest#importFromExcel_parsesBomRootProductCode test`

Expected: FAIL because parser does not read the new column.

- [ ] **Step 3: Implement parser change**

Update BOM import column order to:

`root_product_code / parent_code / child_code / usage_qty / process / equipment / manufacturing_department / manufacturing_unit / mold_cavity / cycle_time / staff_count / takt_time / scrap_rate / version`

Validation rules:
- `root_product_code` required
- `parent_code` required
- `root_product_code` must be a valid code

Set:

```java
b.setRootProductCode(rootProductCode);
```

Update sample SQL inserts to include `root_product_code`.

- [ ] **Step 4: Run focused tests**

Run: `mvn -q -Dtest=ExcelImportServiceTest,ExcelImportControllerTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/main/java/com/aps/service/ExcelImportService.java aps-system/src/test/java/com/aps/service/ExcelImportServiceTest.java aps-system/src/main/resources/schema.sql aps-system/src/main/resources/schema-current.sql
git commit -m "feat: import bom rows with root product scope"
```

### Task 3: Rewrite Calculation To Load BOM Per Finished Product Tree

**Files:**
- Modify: `aps-system/src/main/java/com/aps/service/PlanCalculationService.java`
- Modify: `aps-system/src/test/java/com/aps/service/PlanCalculationServiceTest.java`
- Test: `aps-system/src/test/java/com/aps/integration/PlanCalculationIntegrationTest.java`

- [ ] **Step 1: Write failing tests**

Add tests for:
- shared sub-structure across different finished products does not cross-contaminate
- duplicate rows under the same `root_product_code` still dedupe by `(parent, child)`
- finished product tree traversal only uses rows matching both `version` and `root_product_code`

- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -q -Dtest=PlanCalculationServiceTest,PlanCalculationIntegrationTest test`

Expected: FAIL because calculation currently builds one version-wide BOM index.

- [ ] **Step 3: Implement scoped BOM indexes**

Replace version-wide load:

```java
BomIndexes bomIndexes = buildBomIndexes(version);
```

with per-finished-product load:

```java
BomIndexes bomIndexes = buildBomIndexes(version, fp);
```

Update:
- `buildBomIndexes(String version, String rootProductCode)`
- `processNode(...)` to use the already-scoped index

Keep duplicate-edge dedupe inside each scoped tree:

```java
Map<String, Set<String>> seenChildrenByParentCode = new HashMap<>();
```

so the result remains stable even if the imported BOM contains duplicate rows for the same root product.

- [ ] **Step 4: Run focused tests**

Run: `mvn -q -Dtest=PlanCalculationServiceTest,PlanCalculationIntegrationTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/main/java/com/aps/service/PlanCalculationService.java aps-system/src/test/java/com/aps/service/PlanCalculationServiceTest.java aps-system/src/test/java/com/aps/integration/PlanCalculationIntegrationTest.java
git commit -m "feat: scope plan calculation by bom root product"
```

### Task 4: Update BOM Management UI For Root Product

**Files:**
- Modify: `aps-system/src/main/resources/static/03-bom-list.html`

- [ ] **Step 1: Add the field to BOM list, modal, filter, and import instructions**

Add:
- list column `根完成品`
- filter input `root_product_code`
- modal input `rootProductCode`
- import modal guidance updated to the new column order

- [ ] **Step 2: Update create/edit payloads**

Include:

```javascript
rootProductCode: document.getElementById('bomRootProduct').value.trim()
```

Validate it before submit.

- [ ] **Step 3: Keep tree rendering scoped for readability**

Tree roots should now be derived from rows under the selected or filtered root product where possible, so the visual model matches the new business rule.

- [ ] **Step 4: Manual verification**

Run the app, open `03-bom-list.html`, and verify:
- root product field is visible
- save validates the new field
- import help text matches backend parser order

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/main/resources/static/03-bom-list.html
git commit -m "feat: expose root product scope in bom ui"
```

### Task 5: Add Database Migration And Real-Data Verification

**Files:**
- Modify: `aps-system/src/main/resources/schema.sql`
- Modify: `aps-system/src/main/resources/schema-current.sql`
- Modify: `project.md` (if runtime/data handling notes need update)

- [ ] **Step 1: Add the new column to BOM DDL/migration scripts**

Add `root_product_code` to `t_bom` and document the new required semantics.

- [ ] **Step 2: Add uniqueness recommendation**

At minimum document and, if safe for this schema, enforce:

`(version, root_product_code, parent_code, child_code)`

If `child_code` can be null for leaf process rows, document leaf-row uniqueness separately rather than forcing a broken SQL uniqueness rule.

- [ ] **Step 3: Verify on real data**

Workflow:
1. import BOM with `root_product_code`
2. import demand/inventory/safety/operating data
3. run `/api/production-plan/calculate`
4. verify result row count is reasonable for one root product tree per finished product

- [ ] **Step 4: Capture verification evidence**

Record:
- imported BOM row count
- result row count
- one example finished product with its expected path count

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/main/resources/schema.sql aps-system/src/main/resources/schema-current.sql project.md
git commit -m "chore: document root product scoped bom schema"
```
