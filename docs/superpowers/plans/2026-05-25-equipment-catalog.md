# Equipment Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a standalone equipment catalog master-data module with CRUD, Excel import/export, and use it as the primary device metadata source in the equipment analysis page.

**Architecture:** Keep `t_equipment_catalog` as the single source of truth for equipment metadata and expose it through a dedicated Spring Boot CRUD/import/export API. Leave production-plan persistence unchanged and update the static equipment analysis page to match catalog rows by `manufacturingDepartment + equipment`, falling back to the current defaults only when no catalog row exists.

**Tech Stack:** Java 11, Spring Boot 2.7, Spring Data JPA, Apache POI 5.2.5, JUnit 5, MockMvc, static HTML/Vanilla JS

---

### Task 1: Add Failing Service Tests for Equipment Catalog Rules

**Files:**
- Create: `aps-system/src/test/java/com/aps/service/EquipmentCatalogServiceTest.java`

- [ ] **Step 1: Write the failing duplicate-key validation test**

```java
package com.aps.service;

import com.aps.entity.EquipmentCatalog;
import com.aps.repository.EquipmentCatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentCatalogServiceTest {

    @InjectMocks
    private EquipmentCatalogService service;

    @Mock
    private EquipmentCatalogRepository repository;

    @Test
    void save_rejectsDuplicateDepartmentAndEquipmentModel() {
        when(repository.existsByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(true);

        EquipmentCatalog entity = new EquipmentCatalog(
                null, "制造一部", "冲压设备", "AIDA", "aa001", 3);

        assertThatThrownBy(() -> service.save(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("制造部门+设备小类已存在");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=EquipmentCatalogServiceTest#save_rejectsDuplicateDepartmentAndEquipmentModel test`

Expected: FAIL because `EquipmentCatalogService`, `EquipmentCatalog`, and `EquipmentCatalogRepository` do not exist yet.

- [ ] **Step 3: Add the failing import replacement test**

```java
@Test
void importRows_replacesOnlyTouchedDepartments() {
    EquipmentCatalog oldDeptA = new EquipmentCatalog(1L, "制造一部", "旧大类", "旧品牌", "aa001", 1);
    EquipmentCatalog deptB = new EquipmentCatalog(2L, "制造二部", "焊接设备", "Panasonic", "bb001", 2);

    when(repository.findAll()).thenReturn(java.util.List.of(oldDeptA, deptB));

    java.util.List<EquipmentCatalog> imported = java.util.List.of(
            new EquipmentCatalog(null, "制造一部", "冲压设备", "AIDA", "aa001", 4),
            new EquipmentCatalog(null, "制造一部", "冲压设备", "AIDA", "aa002", 2)
    );

    service.replaceByDepartments(imported);

    org.mockito.Mockito.verify(repository).deleteByManufacturingDepartmentIn(java.util.Set.of("制造一部"));
    org.mockito.Mockito.verify(repository).saveAll(imported);
    org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
            .deleteByManufacturingDepartmentIn(java.util.Set.of("制造二部"));
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -Dtest=EquipmentCatalogServiceTest#importRows_replacesOnlyTouchedDepartments test`

Expected: FAIL because `replaceByDepartments(...)` and repository delete method do not exist yet.

- [ ] **Step 5: Add the failing export-workbook test**

```java
@Test
void exportWorkbook_writesInstructionRowSheetNameAndHeaders() throws Exception {
    when(repository.findAll()).thenReturn(java.util.List.of(
            new EquipmentCatalog(1L, "制造一部", "冲压设备", "AIDA", "aa001", 4)
    ));

    byte[] bytes = service.exportWorkbook();

    try (org.apache.poi.ss.usermodel.Workbook workbook =
                 new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheet("设备清单");
        org.assertj.core.api.Assertions.assertThat(sheet).isNotNull();
        org.assertj.core.api.Assertions.assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                .contains("按制造部门全删全导");
        org.assertj.core.api.Assertions.assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("制造部门");
        org.assertj.core.api.Assertions.assertThat(sheet.getRow(1).getCell(4).getStringCellValue())
                .isEqualTo("台数");
        org.assertj.core.api.Assertions.assertThat(sheet.getRow(2).getCell(3).getStringCellValue())
                .isEqualTo("aa001");
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn -Dtest=EquipmentCatalogServiceTest#exportWorkbook_writesInstructionRowSheetNameAndHeaders test`

Expected: FAIL because `exportWorkbook()` does not exist yet.

- [ ] **Step 7: Commit**

```bash
git add aps-system/src/test/java/com/aps/service/EquipmentCatalogServiceTest.java
git commit -m "test: add equipment catalog service tests"
```

### Task 2: Add Failing Controller Tests for CRUD, Import, and Export

**Files:**
- Create: `aps-system/src/test/java/com/aps/controller/EquipmentCatalogControllerTest.java`

- [ ] **Step 1: Write the failing CRUD controller test**

```java
package com.aps.controller;

import com.aps.entity.EquipmentCatalog;
import com.aps.service.EquipmentCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EquipmentCatalogController.class)
class EquipmentCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EquipmentCatalogService service;

    @Test
    void create_and_findAll_returnCatalogRows() throws Exception {
        EquipmentCatalog saved = new EquipmentCatalog(1L, "制造一部", "冲压设备", "AIDA", "aa001", 4);
        when(service.save(any())).thenReturn(saved);
        when(service.findAll()).thenReturn(List.of(saved));

        mockMvc.perform(post("/api/equipment-catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manufacturingDepartment\":\"制造一部\",\"equipmentCategory\":\"冲压设备\",\"equipmentBrand\":\"AIDA\",\"equipmentModel\":\"aa001\",\"equipmentCount\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipmentModel").value("aa001"));

        mockMvc.perform(get("/api/equipment-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].equipmentBrand").value("AIDA"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=EquipmentCatalogControllerTest#create_and_findAll_returnCatalogRows test`

Expected: FAIL because `EquipmentCatalogController` does not exist yet.

- [ ] **Step 3: Add the failing import controller test**

```java
@Test
void importWorkbook_returnsImportedCount() throws Exception {
    when(service.importWorkbook(any())).thenReturn(new ImportResult(0, 0, 0, 0, 0, 2, java.util.Collections.emptyList()));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/equipment-catalog/import")
                    .file("file", "fake".getBytes()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.importedCount").value(2));
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -Dtest=EquipmentCatalogControllerTest#importWorkbook_returnsImportedCount test`

Expected: FAIL because import endpoint and compatible response body do not exist yet.

- [ ] **Step 5: Add the failing export controller test**

```java
@Test
void exportWorkbook_returnsExcelAttachment() throws Exception {
    when(service.exportWorkbook()).thenReturn(new byte[]{1, 2, 3});

    mockMvc.perform(get("/api/equipment-catalog/export"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                    .string("Content-Disposition", org.hamcrest.Matchers.containsString("template-equipment-catalog.xlsx")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn -Dtest=EquipmentCatalogControllerTest#exportWorkbook_returnsExcelAttachment test`

Expected: FAIL because export endpoint does not exist yet.

- [ ] **Step 7: Commit**

```bash
git add aps-system/src/test/java/com/aps/controller/EquipmentCatalogControllerTest.java
git commit -m "test: add equipment catalog controller tests"
```

### Task 3: Implement Equipment Catalog Domain Model and Service

**Files:**
- Create: `aps-system/src/main/java/com/aps/entity/EquipmentCatalog.java`
- Create: `aps-system/src/main/java/com/aps/repository/EquipmentCatalogRepository.java`
- Create: `aps-system/src/main/java/com/aps/service/EquipmentCatalogService.java`
- Modify: `aps-system/src/main/resources/data-seed.sql`
- Modify: `aps-system/src/main/resources/schema.sql`
- Modify: `aps-system/src/main/resources/schema-current.sql`
- Modify: `project.md`

- [ ] **Step 1: Add the JPA entity**

```java
package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "t_equipment_catalog",
        uniqueConstraints = @UniqueConstraint(name = "uk_equipment_catalog_dept_model",
                columnNames = {"manufacturing_department", "equipment_model"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manufacturing_department", length = 50, nullable = false)
    private String manufacturingDepartment;

    @Column(name = "equipment_category", length = 100, nullable = false)
    private String equipmentCategory;

    @Column(name = "equipment_brand", length = 100, nullable = false)
    private String equipmentBrand;

    @Column(name = "equipment_model", length = 100, nullable = false)
    private String equipmentModel;

    @Column(name = "equipment_count", nullable = false)
    private Integer equipmentCount;
}
```

- [ ] **Step 2: Add the repository**

```java
package com.aps.repository;

import com.aps.entity.EquipmentCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EquipmentCatalogRepository extends JpaRepository<EquipmentCatalog, Long> {

    boolean existsByManufacturingDepartmentAndEquipmentModel(String manufacturingDepartment, String equipmentModel);

    Optional<EquipmentCatalog> findByManufacturingDepartmentAndEquipmentModel(String manufacturingDepartment, String equipmentModel);

    List<EquipmentCatalog> findByManufacturingDepartmentIn(Collection<String> departments);

    void deleteByManufacturingDepartmentIn(Set<String> departments);
}
```

- [ ] **Step 3: Add the service skeleton with validation**

```java
package com.aps.service;

import com.aps.entity.EquipmentCatalog;
import com.aps.repository.EquipmentCatalogRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@Service
@Transactional
public class EquipmentCatalogService {

    @Autowired
    private EquipmentCatalogRepository repository;

    public List<EquipmentCatalog> findAll() {
        return repository.findAll();
    }

    public EquipmentCatalog findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EquipmentCatalog not found: " + id));
    }

    public EquipmentCatalog save(EquipmentCatalog entity) {
        validate(entity);
        if (repository.existsByManufacturingDepartmentAndEquipmentModel(
                entity.getManufacturingDepartment(), entity.getEquipmentModel())) {
            throw new IllegalArgumentException("制造部门+设备小类已存在");
        }
        return repository.save(entity);
    }

    public EquipmentCatalog update(Long id, EquipmentCatalog entity) {
        validate(entity);
        EquipmentCatalog existing = findById(id);
        boolean keyChanged = !existing.getManufacturingDepartment().equals(entity.getManufacturingDepartment())
                || !existing.getEquipmentModel().equals(entity.getEquipmentModel());
        if (keyChanged && repository.existsByManufacturingDepartmentAndEquipmentModel(
                entity.getManufacturingDepartment(), entity.getEquipmentModel())) {
            throw new IllegalArgumentException("制造部门+设备小类已存在");
        }
        existing.setManufacturingDepartment(entity.getManufacturingDepartment());
        existing.setEquipmentCategory(entity.getEquipmentCategory());
        existing.setEquipmentBrand(entity.getEquipmentBrand());
        existing.setEquipmentModel(entity.getEquipmentModel());
        existing.setEquipmentCount(entity.getEquipmentCount());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    void replaceByDepartments(List<EquipmentCatalog> list) {
        Set<String> departments = new LinkedHashSet<>();
        for (EquipmentCatalog item : list) {
            departments.add(item.getManufacturingDepartment());
        }
        repository.deleteByManufacturingDepartmentIn(departments);
        repository.saveAll(list);
    }

    private void validate(EquipmentCatalog entity) {
        if (entity.getManufacturingDepartment() == null || entity.getManufacturingDepartment().trim().isEmpty()) {
            throw new IllegalArgumentException("制造部门不能为空");
        }
        if (entity.getEquipmentCategory() == null || entity.getEquipmentCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("设备大类不能为空");
        }
        if (entity.getEquipmentBrand() == null || entity.getEquipmentBrand().trim().isEmpty()) {
            throw new IllegalArgumentException("设备品牌不能为空");
        }
        if (entity.getEquipmentModel() == null || entity.getEquipmentModel().trim().isEmpty()) {
            throw new IllegalArgumentException("设备小类不能为空");
        }
        if (entity.getEquipmentCount() == null || entity.getEquipmentCount() <= 0) {
            throw new IllegalArgumentException("台数必须大于0");
        }
    }
}
```

- [ ] **Step 4: Add import and export methods**

```java
public ImportResult importWorkbook(InputStream inputStream) throws Exception {
    List<String> errors = new ArrayList<>();
    List<EquipmentCatalog> rows = new ArrayList<>();
    Set<String> uniqueKeys = new LinkedHashSet<>();

    try (Workbook workbook = new XSSFWorkbook(inputStream)) {
        Sheet sheet = workbook.getSheet("设备清单");
        if (sheet == null) {
            throw new IllegalArgumentException("缺少 Sheet: 设备清单");
        }
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String department = ExcelCells.stringCell(row.getCell(0));
            String category = ExcelCells.stringCell(row.getCell(1));
            String brand = ExcelCells.stringCell(row.getCell(2));
            String model = ExcelCells.stringCell(row.getCell(3));
            Integer count = ExcelCells.integerCell(row.getCell(4));

            EquipmentCatalog item = new EquipmentCatalog(null, department, category, brand, model, count);
            validate(item);

            String key = department + "|" + model;
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException("导入文件存在重复键: " + key);
            }
            rows.add(item);
        }
    }

    replaceByDepartments(rows);
    return new ImportResult(0, 0, 0, 0, 0, rows.size(), errors);
}

public byte[] exportWorkbook() throws Exception {
    try (Workbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        Sheet sheet = workbook.createSheet("设备清单");
        sheet.createRow(0).createCell(0).setCellValue("说明：按制造部门全删全导；制造部门+设备小类唯一；台数必须大于0");
        Row header = sheet.createRow(1);
        header.createCell(0).setCellValue("制造部门");
        header.createCell(1).setCellValue("设备大类");
        header.createCell(2).setCellValue("设备品牌");
        header.createCell(3).setCellValue("设备小类");
        header.createCell(4).setCellValue("台数");

        List<EquipmentCatalog> rows = repository.findAll();
        for (int i = 0; i < rows.size(); i++) {
            EquipmentCatalog item = rows.get(i);
            Row row = sheet.createRow(i + 2);
            row.createCell(0).setCellValue(item.getManufacturingDepartment());
            row.createCell(1).setCellValue(item.getEquipmentCategory());
            row.createCell(2).setCellValue(item.getEquipmentBrand());
            row.createCell(3).setCellValue(item.getEquipmentModel());
            row.createCell(4).setCellValue(item.getEquipmentCount());
        }
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}
```

- [ ] **Step 5: Add seed and snapshot rows**

Append representative demo data:

```sql
-- 设备清单
INSERT IGNORE INTO t_equipment_catalog (manufacturing_department, equipment_category, equipment_brand, equipment_model, equipment_count) VALUES
('制造一部', '冲压设备', 'AIDA', 'aa001', 4),
('制造二部', '焊接设备', 'Panasonic', 'bb001', 2),
('制造三部', '检测设备', 'Keyence', 'ff001', 1);
```

Also add `DELETE FROM t_equipment_catalog;` plus matching insert rows in `data-seed.sql`.

- [ ] **Step 6: Update `project.md`**

Add `EquipmentCatalog` / `EquipmentCatalogController` / `template-equipment-catalog.xlsx` to the project inventory.

- [ ] **Step 7: Run tests to verify green**

Run: `mvn -Dtest=EquipmentCatalogServiceTest test`

Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add aps-system/src/main/java/com/aps/entity/EquipmentCatalog.java aps-system/src/main/java/com/aps/repository/EquipmentCatalogRepository.java aps-system/src/main/java/com/aps/service/EquipmentCatalogService.java aps-system/src/main/resources/data-seed.sql aps-system/src/main/resources/schema.sql aps-system/src/main/resources/schema-current.sql project.md
git commit -m "feat: add equipment catalog domain model"
```

### Task 4: Implement Equipment Catalog Controller and Template/Export Surface

**Files:**
- Create: `aps-system/src/main/java/com/aps/controller/EquipmentCatalogController.java`
- Create: `aps-system/src/main/resources/static/template-equipment-catalog.xlsx`
- Create: `template-equipment-catalog.xlsx`

- [ ] **Step 1: Add the controller**

```java
package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.EquipmentCatalog;
import com.aps.service.EquipmentCatalogService;
import com.aps.service.ImportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/equipment-catalog")
@CrossOrigin
public class EquipmentCatalogController {

    @Autowired
    private EquipmentCatalogService service;

    @GetMapping
    public ApiResponse<List<EquipmentCatalog>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<EquipmentCatalog> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ApiResponse<EquipmentCatalog> create(@RequestBody EquipmentCatalog entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<EquipmentCatalog> update(@PathVariable Long id, @RequestBody EquipmentCatalog entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/import")
    public ApiResponse<ImportResult> importWorkbook(@RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.success(service.importWorkbook(file.getInputStream()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkbook() throws Exception {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template-equipment-catalog.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.exportWorkbook());
    }
}
```

- [ ] **Step 2: Build the static template file**

Create `template-equipment-catalog.xlsx` and `aps-system/src/main/resources/static/template-equipment-catalog.xlsx` with:

- Sheet name `设备清单`
- Row 1 merged title cell containing `说明：按制造部门全删全导；制造部门+设备小类唯一；台数必须大于0`
- Row 2 headers `制造部门 / 设备大类 / 设备品牌 / 设备小类 / 台数`
- 2-3 sample rows
- First-row title horizontally centered

- [ ] **Step 3: Run controller tests**

Run: `mvn -Dtest=EquipmentCatalogControllerTest test`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add aps-system/src/main/java/com/aps/controller/EquipmentCatalogController.java aps-system/src/main/resources/static/template-equipment-catalog.xlsx template-equipment-catalog.xlsx
git commit -m "feat: add equipment catalog api and template"
```

### Task 5: Build the Equipment Catalog Maintenance Page and Navigation

**Files:**
- Create: `aps-system/src/main/resources/static/12-equipment-catalog.html`
- Modify: `aps-system/src/main/resources/static/index.html`
- Modify: `aps-system/src/main/resources/static/01-dashboard.html`
- Modify: `aps-system/src/main/resources/static/02-forecast-list.html`
- Modify: `aps-system/src/main/resources/static/03-bom-list.html`
- Modify: `aps-system/src/main/resources/static/04-material-params.html`
- Modify: `aps-system/src/main/resources/static/05-operating-days.html`
- Modify: `aps-system/src/main/resources/static/06-inventory-count.html`
- Modify: `aps-system/src/main/resources/static/07-plan-calculate.html`
- Modify: `aps-system/src/main/resources/static/08-plan-result.html`
- Modify: `aps-system/src/main/resources/static/09-capacity-calendar.html`
- Modify: `aps-system/src/main/resources/static/10-workforce-report.html`
- Modify: `aps-system/src/main/resources/static/11-equipment-load.html`

- [ ] **Step 1: Create the new maintenance page**

Base the page on `03-bom-list.html` and change the data model to:

```javascript
const API = location.origin;
let allRows = [];
let filteredRows = [];

async function loadData() {
  const response = await fetch(`${API}/api/equipment-catalog`);
  const json = await response.json();
  allRows = json.data || [];
  applyFilter();
  updateStats(allRows);
}

function applyFilter() {
  const department = document.getElementById('filterDepartment').value.trim().toLowerCase();
  const category = document.getElementById('filterCategory').value.trim().toLowerCase();
  const brand = document.getElementById('filterBrand').value.trim().toLowerCase();
  const model = document.getElementById('filterModel').value.trim().toLowerCase();

  filteredRows = allRows.filter(row =>
    (!department || row.manufacturingDepartment.toLowerCase().includes(department)) &&
    (!category || row.equipmentCategory.toLowerCase().includes(category)) &&
    (!brand || row.equipmentBrand.toLowerCase().includes(brand)) &&
    (!model || row.equipmentModel.toLowerCase().includes(model))
  );
  renderListView(filteredRows);
}

async function saveCatalog() {
  const id = document.getElementById('catalogId').value;
  const body = {
    manufacturingDepartment: document.getElementById('catalogDepartment').value.trim(),
    equipmentCategory: document.getElementById('catalogCategory').value.trim(),
    equipmentBrand: document.getElementById('catalogBrand').value.trim(),
    equipmentModel: document.getElementById('catalogModel').value.trim(),
    equipmentCount: parseInt(document.getElementById('catalogCount').value, 10)
  };
  const url = id ? `${API}/api/equipment-catalog/${id}` : `${API}/api/equipment-catalog`;
  const method = id ? 'PUT' : 'POST';
  const response = await fetch(url, {
    method,
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body)
  });
  const json = await response.json();
  if (json.code === 200) {
    closeCatalogModal();
    loadData();
  }
}

async function confirmImport() {
  const file = document.getElementById('importFile').files[0];
  const formData = new FormData();
  formData.append('file', file);
  const response = await fetch(`${API}/api/equipment-catalog/import`, {
    method: 'POST',
    body: formData
  });
  const json = await response.json();
  document.getElementById('importResult').innerHTML = `导入成功：<b>${json.data.importedCount || 0}</b> 条`;
  loadData();
}
```

Page sections to include:

- KPI cards for `制造部门数 / 设备小类数 / 总台数`
- filter bar
- toolbar with `下载模板 / 批量导入 / 新增 / 导出`
- list columns `制造部门 / 设备大类 / 设备品牌 / 设备小类 / 台数 / 操作`
- modal for add/edit
- import modal with “按制造部门全删全导” note

- [ ] **Step 2: Add the sidebar navigation entry to all static pages**

Insert this link into the “基础数据” group, immediately after `BOM 管理`:

```html
<a href="12-equipment-catalog.html" class="nav-item"><span class="ni">⌘</span>设备清单</a>
```

For `12-equipment-catalog.html` itself, mark the link as:

```html
<a href="12-equipment-catalog.html" class="nav-item active"><span class="ni">⌘</span>设备清单</a>
```

- [ ] **Step 3: Add the homepage card**

In `index.html`, add a card linking to `12-equipment-catalog.html`:

```html
<a class="card" href="12-equipment-catalog.html">
  <div class="card-top"><span class="card-badge">基础数据</span><span class="card-icon">⌘</span></div>
  <div class="card-title">设备清单</div>
  <div class="card-desc">维护制造部门下的设备大类、品牌、小类和台数，并为设备分析提供真实主数据。</div>
  <div class="card-foot"><span>设备主数据</span><span class="card-arrow">进入 →</span></div>
</a>
```

- [ ] **Step 4: Manual verification of the new page**

Open: `http://localhost:8080/12-equipment-catalog.html`

Expected:

- page loads without JS errors
- list can load `/api/equipment-catalog`
- add/edit/delete/import/export buttons call the right endpoints
- navigation highlights “设备清单”

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/main/resources/static/12-equipment-catalog.html aps-system/src/main/resources/static/index.html aps-system/src/main/resources/static/01-dashboard.html aps-system/src/main/resources/static/02-forecast-list.html aps-system/src/main/resources/static/03-bom-list.html aps-system/src/main/resources/static/04-material-params.html aps-system/src/main/resources/static/05-operating-days.html aps-system/src/main/resources/static/06-inventory-count.html aps-system/src/main/resources/static/07-plan-calculate.html aps-system/src/main/resources/static/08-plan-result.html aps-system/src/main/resources/static/09-capacity-calendar.html aps-system/src/main/resources/static/10-workforce-report.html aps-system/src/main/resources/static/11-equipment-load.html
git commit -m "feat: add equipment catalog maintenance page"
```

### Task 6: Integrate Equipment Analysis with the Catalog

**Files:**
- Modify: `aps-system/src/main/resources/static/11-equipment-load.html`

- [ ] **Step 1: Add catalog loading and key matching**

Replace the current one-source analysis flow with:

```javascript
let planData = [];
let odData = [];
let catalogData = [];
let rows = [];

function buildCatalogMap(data) {
  const map = new Map();
  data.forEach(item => {
    const key = `${item.manufacturingDepartment || ''}|${item.equipmentModel || ''}`;
    map.set(key, item);
  });
  return map;
}

function buildRows(data, workDaysMap, catalogMap) {
  const grouped = new Map();
  data.forEach(item => {
    if (!item.yearMonth || !item.equipment) return;

    const catalogKey = `${item.manufacturingDepartment || ''}|${item.equipment || ''}`;
    const catalog = catalogMap.get(catalogKey);
    const equipmentCategory = catalog?.equipmentCategory || item.process || item.equipment || '—';
    const equipmentBrand = catalog?.equipmentBrand || '—';
    const equipmentModel = catalog?.equipmentModel || item.equipment || '—';
    const equipmentCount = catalog?.equipmentCount || 1;
    const matched = !!catalog;

    const key = [item.manufacturingDepartment || '—', equipmentCategory, equipmentModel, item.yearMonth].join('|');
    if (!grouped.has(key)) {
      grouped.set(key, {
        manufacturingDepartment: item.manufacturingDepartment || '—',
        equipmentCategory,
        equipmentBrand,
        equipmentModel,
        equipmentCount,
        yearMonth: String(item.yearMonth),
        workDays: Number(workDaysMap[String(item.yearMonth)] || 0),
        requiredMachineCount: 0,
        matchedCatalog: matched
      });
    }

    const row = grouped.get(key);
    const moldCavity = Math.max(Number(item.moldCavity || 0), 1);
    const cycleTime = Number(item.cycleTime || 0);
    const planQty = Number(item.planQty || 0);
    const availableSeconds = row.workDays * getDailyHours() * 3600;
    const requiredSeconds = moldCavity > 0 ? (planQty * cycleTime) / moldCavity : 0;
    row.requiredMachineCount += availableSeconds > 0 ? requiredSeconds / availableSeconds : 0;
  });

  return [...grouped.values()].map(row => ({
    ...row,
    difference: row.equipmentCount - row.requiredMachineCount,
    loadRate: row.equipmentCount > 0 ? row.requiredMachineCount / row.equipmentCount : 0
  }));
}
```

- [ ] **Step 2: Update the data-fetch pipeline**

```javascript
async function loadData() {
  const version = document.getElementById('versionSel')?.value || '';
  if (!version) {
    rows = [];
    renderTable([]);
    updateStats([]);
    return;
  }

  const [planRes, odRes, catalogRes] = await Promise.all([
    fetch(`${API}/api/production-plan/by-version/${encodeURIComponent(version)}`).then(r => r.json()),
    fetch(`${API}/api/operating-days`).then(r => r.json()),
    fetch(`${API}/api/equipment-catalog`).then(r => r.json())
  ]);

  planData = planRes.data || [];
  odData = odRes.data || [];
  catalogData = catalogRes.data || [];

  const workDaysMap = {};
  odData.forEach(item => {
    if (item.yearMonth != null) {
      workDaysMap[String(item.yearMonth)] = item.workDays || 0;
    }
  });

  rows = buildRows(planData, workDaysMap, buildCatalogMap(catalogData));
  setOptions('monthSel', [...new Set(rows.map(row => row.yearMonth))], '全部月份');
  setOptions('deptSel', [...new Set(rows.map(row => row.manufacturingDepartment))], '全部部门');
  applyFilters();
}
```

- [ ] **Step 3: Update the note and render unmatched markers**

Replace the current note with:

```html
<div class="note-row">说明：设备分析优先匹配设备清单主数据；未命中设备台账时，设备品牌显示为“—”，设备大类默认取工序，设备小类默认取设备编码，台数按 1 台测算。</div>
```

In table rendering, add an unmatched badge:

```javascript
<td>${row.equipmentBrand}${row.matchedCatalog ? '' : '<span style="margin-left:6px;color:#f57c00;font-size:11px">未匹配</span>'}</td>
```

- [ ] **Step 4: Manual verification of equipment analysis**

Open: `http://localhost:8080/11-equipment-load.html`

Expected:

- matched rows show real `设备大类 / 设备品牌 / 台数`
- unmatched rows still render and show the fallback marker
- load-rate math changes only because `台数` may now be greater than `1`

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/main/resources/static/11-equipment-load.html
git commit -m "feat: integrate equipment analysis with equipment catalog"
```

### Task 7: Final Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-05-25-equipment-catalog-design.md` (only if implementation drift requires clarification)

- [ ] **Step 1: Run focused backend tests**

Run:

`mvn -Dtest=EquipmentCatalogServiceTest,EquipmentCatalogControllerTest test`

Expected: PASS

- [ ] **Step 2: Run regression tests around existing equipment analysis backends**

Run:

`mvn -Dtest=EquipmentLoadServiceTest,EquipmentLoadControllerTest,ProductionPlanControllerTest test`

Expected: PASS, confirming the new catalog API did not break existing plan/equipment endpoints.

- [ ] **Step 3: Verify template assets and static pages**

Check:

```bash
git diff --name-only -- aps-system/src/main/resources/static template-equipment-catalog.xlsx
```

Expected: only `12-equipment-catalog.html`, `11-equipment-load.html`, nav-touched static pages, and the new template files are listed.

- [ ] **Step 4: Inspect final diff scope**

Run:

`git diff --stat`

Expected: changes limited to equipment catalog domain, templates, static UI, seed/snapshot files, and tests.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-05-25-equipment-catalog.md
git commit -m "chore: finalize equipment catalog implementation plan"
```
