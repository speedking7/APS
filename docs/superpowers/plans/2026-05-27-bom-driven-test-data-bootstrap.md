# BOM-Driven Test Data Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the current approved BOM as a repo baseline and add startup-time empty-table bootstrap logic that derives the rest of the local test data from that BOM without overwriting existing manual data.

**Architecture:** Split the work into three bounded units: a BOM baseline resource, a Spring startup bootstrapper plus a BOM-derived data generator, and verification coverage for empty-table bootstrap, non-overwrite behavior, and derivation rules. Leave `forecast` as a legacy path and move the real local test-data path to `demand`.

**Tech Stack:** Java 11, Spring Boot 2.7, Spring Data JPA, MySQL 8, JUnit 5, Mockito, H2 or existing Spring test stack, PowerShell/WSL verification commands

---

### Task 1: Capture the Current BOM Baseline

**Files:**
- Create: `aps-system/src/main/resources/bootstrap/test-bom-seed.sql`
- Modify: `docs/superpowers/specs/2026-05-27-bom-driven-test-data-bootstrap-design.md` (only if the exported baseline reveals structural assumptions that need clarification)

- [ ] **Step 1: Inspect the live BOM rows currently in `aps_db`**

Run:

```powershell
wsl bash -lc "mysql -h 127.0.0.1 -P 3307 -uroot -proot -D aps_db -e \"SELECT parent_code,child_code,usage_qty,process,equipment,manufacturing_department,manufacturing_unit,mold_cavity,cycle_time,staff_count,takt_time,scrap_rate,version FROM t_bom ORDER BY parent_code, child_code;\""
```

Expected: a complete ordered snapshot of the currently approved BOM rows.

- [ ] **Step 2: Write the baseline SQL resource**

Create `aps-system/src/main/resources/bootstrap/test-bom-seed.sql` with:

```sql
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM t_bom;
INSERT INTO t_bom (
  parent_code,
  child_code,
  usage_qty,
  process,
  equipment,
  manufacturing_department,
  manufacturing_unit,
  mold_cavity,
  cycle_time,
  staff_count,
  takt_time,
  scrap_rate,
  version
) VALUES
  -- export current approved rows here, one row per live BOM record
  (...);
SET FOREIGN_KEY_CHECKS=1;
```

Use the exact row order from the inspection query so future diffs are stable.

- [ ] **Step 3: Verify the new resource is present and non-empty**

Run:

```powershell
Get-Content .\aps-system\src\main\resources\bootstrap\test-bom-seed.sql
```

Expected: the file exists and contains the current approved BOM baseline.

- [ ] **Step 4: Commit**

```bash
git add aps-system/src/main/resources/bootstrap/test-bom-seed.sql
git commit -m "chore: persist approved bom test baseline"
```

### Task 2: Add Failing Tests for Empty-Table Bootstrap

**Files:**
- Create: `aps-system/src/test/java/com/aps/bootstrap/TestDataBootstrapperTest.java`
- Create: `aps-system/src/test/java/com/aps/bootstrap/BomDerivedTestDataGeneratorTest.java`

- [ ] **Step 1: Write the failing startup bootstrap test for empty-table initialization**

Create `TestDataBootstrapperTest.java` with coverage like:

```java
@ExtendWith(MockitoExtension.class)
class TestDataBootstrapperTest {

    @InjectMocks
    private TestDataBootstrapper bootstrapper;

    @Mock private BomRepository bomRepository;
    @Mock private PartMasterRepository partMasterRepository;
    @Mock private EquipmentCatalogRepository equipmentCatalogRepository;
    @Mock private SafetyStockRepository safetyStockRepository;
    @Mock private OperatingDaysRepository operatingDaysRepository;
    @Mock private InventoryCountRepository inventoryCountRepository;
    @Mock private SharedMoldRuleRepository sharedMoldRuleRepository;
    @Mock private DemandRepository demandRepository;
    @Mock private BomBaselineLoader bomBaselineLoader;
    @Mock private BomDerivedTestDataGenerator generator;

    @Test
    void run_loadsBaselineAndGeneratesDerivedData_whenTargetTablesAreEmpty() {
        when(bomRepository.count()).thenReturn(0L);
        when(partMasterRepository.count()).thenReturn(0L);
        when(equipmentCatalogRepository.count()).thenReturn(0L);
        when(safetyStockRepository.count()).thenReturn(0L);
        when(operatingDaysRepository.count()).thenReturn(0L);
        when(inventoryCountRepository.count()).thenReturn(0L);
        when(sharedMoldRuleRepository.count()).thenReturn(0L);
        when(demandRepository.count()).thenReturn(0L);

        bootstrapper.run(new DefaultApplicationArguments(new String[0]));

        verify(bomBaselineLoader).loadIfBomEmpty();
        verify(generator).seedPartMasterIfEmpty();
        verify(generator).seedEquipmentCatalogIfEmpty();
        verify(generator).seedOperatingDaysIfEmpty();
        verify(generator).seedInventoryCountIfEmpty();
        verify(generator).seedSafetyStockIfEmpty();
        verify(generator).seedSharedMoldRulesIfEmpty();
        verify(generator).seedDemandIfEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd .\aps-system
mvn "-Dtest=TestDataBootstrapperTest" test
```

Expected: FAIL because `TestDataBootstrapper`, `BomBaselineLoader`, and `BomDerivedTestDataGenerator` do not exist yet.

- [ ] **Step 3: Write the failing non-overwrite bootstrap test**

Add:

```java
@Test
void run_skipsGeneration_whenTablesAlreadyContainData() {
    when(bomRepository.count()).thenReturn(5L);
    when(partMasterRepository.count()).thenReturn(3L);
    when(equipmentCatalogRepository.count()).thenReturn(2L);
    when(safetyStockRepository.count()).thenReturn(4L);
    when(operatingDaysRepository.count()).thenReturn(3L);
    when(inventoryCountRepository.count()).thenReturn(4L);
    when(sharedMoldRuleRepository.count()).thenReturn(1L);
    when(demandRepository.count()).thenReturn(6L);

    bootstrapper.run(new DefaultApplicationArguments(new String[0]));

    verify(bomBaselineLoader, never()).loadIfBomEmpty();
    verifyNoInteractions(generator);
}
```

- [ ] **Step 4: Run test to verify it fails**

Run:

```powershell
cd .\aps-system
mvn "-Dtest=TestDataBootstrapperTest" test
```

Expected: FAIL on missing bootstrapper classes.

- [ ] **Step 5: Write the failing derivation rules test**

Create `BomDerivedTestDataGeneratorTest.java` with focused rule checks:

```java
@ExtendWith(MockitoExtension.class)
class BomDerivedTestDataGeneratorTest {

    @InjectMocks
    private BomDerivedTestDataGenerator generator;

    @Mock private BomRepository bomRepository;
    @Mock private PartMasterRepository partMasterRepository;
    @Mock private EquipmentCatalogRepository equipmentCatalogRepository;
    @Mock private SafetyStockRepository safetyStockRepository;
    @Mock private OperatingDaysRepository operatingDaysRepository;
    @Mock private InventoryCountRepository inventoryCountRepository;
    @Mock private SharedMoldRuleRepository sharedMoldRuleRepository;
    @Mock private DemandRepository demandRepository;

    @Test
    void seedDemandIfEmpty_generatesRowsOnlyForBomRoots() {
        List<Bom> boms = List.of(
            bom("FP-A", "SA-A1", "EQ-1", "制造一部"),
            bom("SA-A1", "RM-A1", "EQ-2", "制造二部"),
            bom("FP-B", "SA-B1", "EQ-1", "制造一部")
        );
        when(demandRepository.count()).thenReturn(0L);
        when(bomRepository.findAll()).thenReturn(boms);

        generator.seedDemandIfEmpty();

        ArgumentCaptor<List<Demand>> captor = ArgumentCaptor.forClass(List.class);
        verify(demandRepository).saveAll(captor.capture());
        List<Demand> saved = captor.getValue();

        assertThat(saved).allMatch(d -> Set.of("FP-A", "FP-B").contains(d.getItemCode()));
        assertThat(saved).noneMatch(d -> d.getItemCode().equals("SA-A1"));
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run:

```powershell
cd .\aps-system
mvn "-Dtest=BomDerivedTestDataGeneratorTest" test
```

Expected: FAIL because the generator does not exist yet.

- [ ] **Step 7: Commit**

```bash
git add aps-system/src/test/java/com/aps/bootstrap/TestDataBootstrapperTest.java aps-system/src/test/java/com/aps/bootstrap/BomDerivedTestDataGeneratorTest.java
git commit -m "test: add bom-driven bootstrap coverage"
```

### Task 3: Implement the BOM Baseline Loader

**Files:**
- Create: `aps-system/src/main/java/com/aps/bootstrap/BomBaselineLoader.java`
- Modify: `aps-system/pom.xml` (only if a SQL resource execution dependency is missing; otherwise do not change it)

- [ ] **Step 1: Implement a dedicated BOM baseline loader**

Create:

```java
package com.aps.bootstrap;

import com.aps.repository.BomRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class BomBaselineLoader {

    private final BomRepository bomRepository;
    private final DataSource dataSource;

    public BomBaselineLoader(BomRepository bomRepository, DataSource dataSource) {
        this.bomRepository = bomRepository;
        this.dataSource = dataSource;
    }

    public void loadIfBomEmpty() throws Exception {
        if (bomRepository.count() > 0) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("bootstrap/test-bom-seed.sql"));
        }
    }
}
```

- [ ] **Step 2: Re-run the bootstrap test**

Run:

```powershell
cd .\aps-system
mvn "-Dtest=TestDataBootstrapperTest" test
```

Expected: still FAIL because the startup bootstrapper and generator are not implemented yet, but the missing-class failure should have narrowed.

- [ ] **Step 3: Commit**

```bash
git add aps-system/src/main/java/com/aps/bootstrap/BomBaselineLoader.java
git commit -m "feat: add bom baseline sql loader"
```

### Task 4: Implement the BOM-Derived Generator

**Files:**
- Create: `aps-system/src/main/java/com/aps/bootstrap/BomDerivedTestDataGenerator.java`
- Modify: `aps-system/src/main/java/com/aps/entity/SharedMoldRule.java` (only if needed to support generated default fields cleanly)

- [ ] **Step 1: Implement BOM root and material collection helpers**

Create helper methods inside `BomDerivedTestDataGenerator`:

```java
List<Bom> loadAllBom() { ... }

Set<String> collectAllMaterialCodes(List<Bom> boms) { ... }

Set<String> findRootCodes(List<Bom> boms) {
    Set<String> parents = ...;
    Set<String> children = ...; // non-null child_code only
    parents.removeAll(children);
    return parents;
}

Set<String> findNonRootCodes(List<Bom> boms) {
    Set<String> all = collectAllMaterialCodes(boms);
    all.removeAll(findRootCodes(boms));
    return all;
}
```

- [ ] **Step 2: Implement `seedPartMasterIfEmpty()`**

Use:

```java
if (partMasterRepository.count() > 0) return;
List<PartMaster> rows = ... // one row per unique material code
partMasterRepository.saveAll(rows);
```

Rules to encode:
- root → `成品-编码`
- parent and child → `半成品-编码`
- child only → `零件-编码`
- `productNo = "P-" + code`
- `projectName` stable by root grouping

- [ ] **Step 3: Implement `seedEquipmentCatalogIfEmpty()`**

Use grouped BOM rows by `(manufacturingDepartment, equipment)` and save:

```java
new EquipmentCatalog(
    null,
    department,
    "自动生成设备",
    "AUTO",
    equipment,
    count
)
```

Skip BOM rows with blank department or equipment.

- [ ] **Step 4: Implement `seedOperatingDaysIfEmpty()`**

Generate 3 months from a single base month helper:

```java
List<OperatingDays> rows = List.of(
    new OperatingDays(null, yyyyMm1, 26, 21, 5, 0),
    new OperatingDays(null, yyyyMm2, 26, 21, 5, 0),
    new OperatingDays(null, yyyyMm3, 26, 21, 5, 0)
);
```

- [ ] **Step 5: Implement `seedInventoryCountIfEmpty()` and `seedSafetyStockIfEmpty()`**

Inventory:

```java
if (inventoryCountRepository.count() > 0) return;
for (String code : nonRootCodes) {
    rows.add(new InventoryCount(null, code, previousMonth, qty, AUTO_VERSION));
}
inventoryCountRepository.saveAll(rows);
```

Safety stock:

```java
if (safetyStockRepository.count() > 0) return;
for (String code : nonRootCodes) {
    for (Integer period : periods) {
        rows.add(new SafetyStock(null, code, period, dailyEquivalent, 3.0, 15.0, AUTO_VERSION));
    }
}
safetyStockRepository.saveAll(rows);
```

- [ ] **Step 6: Implement `seedSharedMoldRulesIfEmpty()`**

Rule:

```java
if (sharedMoldRuleRepository.count() > 0) return;
List<String> roots = new ArrayList<>(findRootCodes(boms));
Collections.sort(roots);
for (int i = 0; i + 1 < roots.size(); i += 2) {
    rows.add(new SharedMoldRule(null, roots.get(i), roots.get(i + 1), null, null, true, "自动生成测试规则"));
}
sharedMoldRuleRepository.saveAll(rows);
```

- [ ] **Step 7: Implement `seedDemandIfEmpty()`**

Rule:

```java
if (demandRepository.count() > 0) return;
for (String root : sortedRoots) {
    for (int i = 0; i < periods.size(); i++) {
        double demandQty = 100 + rootIndex * 10 + i;
        double endingInventory = 5;
        rows.add(new Demand(null, "AUTO", root, period, demandQty, endingInventory, 0.0, demandQty - endingInventory, AUTO_VERSION));
    }
}
demandRepository.saveAll(rows);
```

- [ ] **Step 8: Run the generator rule tests**

Run:

```powershell
cd .\aps-system
mvn "-Dtest=BomDerivedTestDataGeneratorTest" test
```

Expected: PASS for root-only demand generation and any other rule assertions added in the test file.

- [ ] **Step 9: Commit**

```bash
git add aps-system/src/main/java/com/aps/bootstrap/BomDerivedTestDataGenerator.java
git commit -m "feat: derive local test data from bom baseline"
```

### Task 5: Implement the Startup Bootstrapper

**Files:**
- Create: `aps-system/src/main/java/com/aps/bootstrap/TestDataBootstrapper.java`

- [ ] **Step 1: Add the startup runner**

Create:

```java
package com.aps.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TestDataBootstrapper implements ApplicationRunner {

    private final BomBaselineLoader bomBaselineLoader;
    private final BomDerivedTestDataGenerator generator;

    public TestDataBootstrapper(BomBaselineLoader bomBaselineLoader, BomDerivedTestDataGenerator generator) {
        this.bomBaselineLoader = bomBaselineLoader;
        this.generator = generator;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        bomBaselineLoader.loadIfBomEmpty();
        generator.seedPartMasterIfEmpty();
        generator.seedEquipmentCatalogIfEmpty();
        generator.seedOperatingDaysIfEmpty();
        generator.seedInventoryCountIfEmpty();
        generator.seedSafetyStockIfEmpty();
        generator.seedSharedMoldRulesIfEmpty();
        generator.seedDemandIfEmpty();
    }
}
```

- [ ] **Step 2: Re-run the bootstrap tests**

Run:

```powershell
cd .\aps-system
mvn "-Dtest=TestDataBootstrapperTest" test
```

Expected: PASS for both empty-table and non-overwrite coverage.

- [ ] **Step 3: Commit**

```bash
git add aps-system/src/main/java/com/aps/bootstrap/TestDataBootstrapper.java
git commit -m "feat: bootstrap local test data on startup"
```

### Task 6: Verify Live Bootstrap Against the Local Database

**Files:**
- Modify: `scripts/aps-local.sh` (only if startup arguments need a small log message or deterministic init behavior; otherwise do not change it)
- Modify: `docs/local-startup.md`

- [ ] **Step 1: Back up current relevant table counts**

Run:

```powershell
wsl bash -lc "mysql -h 127.0.0.1 -P 3307 -uroot -proot -D aps_db -e \"SELECT 't_bom' AS table_name, COUNT(1) AS row_count FROM t_bom UNION ALL SELECT 't_part_master', COUNT(1) FROM t_part_master UNION ALL SELECT 't_equipment_catalog', COUNT(1) FROM t_equipment_catalog UNION ALL SELECT 't_safety_stock', COUNT(1) FROM t_safety_stock UNION ALL SELECT 't_operating_days', COUNT(1) FROM t_operating_days UNION ALL SELECT 't_inventory_count', COUNT(1) FROM t_inventory_count UNION ALL SELECT 't_shared_mold_rule', COUNT(1) FROM t_shared_mold_rule UNION ALL SELECT 't_demand', COUNT(1) FROM t_demand;\""
```

Expected: current row counts before clearing tables.

- [ ] **Step 2: Clear only the target bootstrap tables**

Run:

```powershell
wsl bash -lc "mysql -h 127.0.0.1 -P 3307 -uroot -proot -D aps_db -e \"SET FOREIGN_KEY_CHECKS=0; DELETE FROM t_demand; DELETE FROM t_shared_mold_rule; DELETE FROM t_inventory_count; DELETE FROM t_safety_stock; DELETE FROM t_operating_days; DELETE FROM t_equipment_catalog; DELETE FROM t_part_master; DELETE FROM t_bom; SET FOREIGN_KEY_CHECKS=1;\""
```

Expected: all target tables empty.

- [ ] **Step 3: Restart the local app**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-aps-local.ps1 -Port 8081
```

Expected: app restarts successfully and logs report healthy startup.

- [ ] **Step 4: Verify that all target tables were repopulated**

Run:

```powershell
wsl bash -lc "mysql -h 127.0.0.1 -P 3307 -uroot -proot -D aps_db -e \"SELECT 't_bom' AS table_name, COUNT(1) AS row_count FROM t_bom UNION ALL SELECT 't_part_master', COUNT(1) FROM t_part_master UNION ALL SELECT 't_equipment_catalog', COUNT(1) FROM t_equipment_catalog UNION ALL SELECT 't_safety_stock', COUNT(1) FROM t_safety_stock UNION ALL SELECT 't_operating_days', COUNT(1) FROM t_operating_days UNION ALL SELECT 't_inventory_count', COUNT(1) FROM t_inventory_count UNION ALL SELECT 't_shared_mold_rule', COUNT(1) FROM t_shared_mold_rule UNION ALL SELECT 't_demand', COUNT(1) FROM t_demand;\""
```

Expected: all target tables show non-zero rows, except `t_shared_mold_rule` which may be zero if fewer than two BOM roots exist.

- [ ] **Step 5: Verify `forecast` remains untouched by the new bootstrap**

Run:

```powershell
wsl bash -lc "mysql -h 127.0.0.1 -P 3307 -uroot -proot -D aps_db -e \"SELECT COUNT(1) AS forecast_rows FROM t_forecast;\""
```

Expected: no requirement to repopulate it; the bootstrap should not depend on this table.

- [ ] **Step 6: Update local startup documentation**

Document in `docs/local-startup.md`:
- the empty-table auto-bootstrap behavior
- the target tables
- the fact that `forecast` is now legacy and not part of the local bootstrap path
- how to replace the BOM baseline later

- [ ] **Step 7: Commit**

```bash
git add docs/local-startup.md
git commit -m "docs: describe bom-driven local test bootstrap"
```

### Task 7: Run Focused Regression Tests and Final Verification

**Files:**
- Modify: `project.md` (if the runtime notes need an explicit mention of BOM-driven test bootstrap)

- [ ] **Step 1: Run focused bootstrap tests**

Run:

```powershell
cd .\aps-system
mvn "-Dtest=TestDataBootstrapperTest,BomDerivedTestDataGeneratorTest" test
```

Expected: PASS.

- [ ] **Step 2: Run any impacted existing tests**

Run:

```powershell
cd .\aps-system
mvn "-Dtest=PlanCalculationServiceTest,PlanCalculationIntegrationTest,EquipmentCatalogServiceTest,PartMasterServiceTest" test
```

Expected: PASS, proving the generated support data does not break calculation or existing master-data behavior.

- [ ] **Step 3: Verify the app health endpoint after all changes**

Run:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8081/actuator/health
```

Expected: HTTP 200 with health payload.

- [ ] **Step 4: Optional documentation note in `project.md`**

Add a short runtime note if needed:

```markdown
- Local startup auto-bootstrap fills empty BOM-driven test tables from the repo BOM baseline. It does not overwrite existing data and does not initialize `t_forecast`.
```

- [ ] **Step 5: Commit**

```bash
git add project.md aps-system/src/main/java/com/aps/bootstrap aps-system/src/test/java/com/aps/bootstrap docs/local-startup.md
git commit -m "feat: bootstrap bom-driven local test data"
```
