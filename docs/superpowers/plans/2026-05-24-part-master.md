# Part Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a standalone part master table and APIs, then enhance production plan query responses with part attributes for both `itemCode` and `finishedProductCode`.

**Architecture:** Keep `t_part_master` as the single source of truth for part metadata and leave `t_production_plan` unchanged. Build a `ProductionPlanView` DTO in the query layer by batch-loading all referenced part numbers and mapping them onto each plan row without N+1 queries.

**Tech Stack:** Java 11, Spring Boot 2.7, Spring Data JPA, JUnit 5, MockMvc, H2

---

### Task 1: Add Failing Tests for Part Master CRUD

**Files:**
- Create: `aps-system/src/test/java/com/aps/controller/PartMasterControllerTest.java`
- Create: `aps-system/src/test/java/com/aps/service/PartMasterServiceTest.java`

- [ ] **Step 1: Write the failing controller test for create/query**

```java
@WebMvcTest(PartMasterController.class)
class PartMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PartMasterService partMasterService;

    @Test
    void create_and_queryByPartNo_returnPartMaster() throws Exception {
        PartMaster saved = new PartMaster(1L, "P001", "前保险杠", "PN-001", "A项目");
        when(partMasterService.save(any())).thenReturn(saved);
        when(partMasterService.findByPartNo("P001")).thenReturn(saved);

        mockMvc.perform(post("/api/part-master")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partNo\":\"P001\",\"productName\":\"前保险杠\",\"productNo\":\"PN-001\",\"projectName\":\"A项目\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partNo").value("P001"));

        mockMvc.perform(get("/api/part-master/by-part-no/P001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("前保险杠"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PartMasterControllerTest test`
Expected: FAIL because `PartMasterController` and related types do not exist yet.

- [ ] **Step 3: Write the failing service test for unique part number handling**

```java
@ExtendWith(MockitoExtension.class)
class PartMasterServiceTest {

    @InjectMocks
    private PartMasterService service;

    @Mock
    private PartMasterRepository repository;

    @Test
    void save_rejectsDuplicatePartNo() {
        when(repository.existsByPartNo("P001")).thenReturn(true);

        PartMaster input = new PartMaster(null, "P001", "前保险杠", "PN-001", "A项目");

        assertThatThrownBy(() -> service.save(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partNo already exists");
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -Dtest=PartMasterServiceTest test`
Expected: FAIL because `PartMasterService` and `PartMasterRepository` do not exist yet.

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/test/java/com/aps/controller/PartMasterControllerTest.java aps-system/src/test/java/com/aps/service/PartMasterServiceTest.java
git commit -m "test: add part master api and service tests"
```

### Task 2: Add Failing Tests for Production Plan Enrichment

**Files:**
- Modify: `aps-system/src/test/java/com/aps/service/ProductionPlanServiceTest.java`

- [ ] **Step 1: Write the failing enrichment test**

```java
@ExtendWith(MockitoExtension.class)
class ProductionPlanServiceTest {

    @InjectMocks
    private ProductionPlanService service;

    @Mock
    private ProductionPlanRepository repository;

    @Mock
    private PartMasterRepository partMasterRepository;

    @Test
    void findByVersion_enrichesItemAndFinishedProductAttributes() {
        ProductionPlan plan = new ProductionPlan();
        plan.setFinishedProductCode("FP001");
        plan.setItemCode("C001");
        plan.setVersion("v1");

        when(repository.findByVersion("v1")).thenReturn(List.of(plan));
        when(partMasterRepository.findByPartNoIn(Set.of("FP001", "C001")))
                .thenReturn(List.of(
                        new PartMaster(1L, "FP001", "整机", "FNO-1", "项目A"),
                        new PartMaster(2L, "C001", "支架", "CNO-1", "项目A")));

        List<ProductionPlanView> views = service.findViewsByVersion("v1");

        assertThat(views).hasSize(1);
        ProductionPlanView view = views.get(0);
        assertThat(view.getItemProductName()).isEqualTo("支架");
        assertThat(view.getFinishedProductName()).isEqualTo("整机");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ProductionPlanServiceTest test`
Expected: FAIL because `ProductionPlanView` and enrichment logic do not exist yet.

- [ ] **Step 3: Add the missing-part fallback test**

```java
@Test
void findByVersion_returnsNullExtendedFieldsWhenPartMasterMissing() {
    ProductionPlan plan = new ProductionPlan();
    plan.setFinishedProductCode("FP001");
    plan.setItemCode("C001");
    plan.setVersion("v1");

    when(repository.findByVersion("v1")).thenReturn(List.of(plan));
    when(partMasterRepository.findByPartNoIn(Set.of("FP001", "C001")))
            .thenReturn(Collections.emptyList());

    ProductionPlanView view = service.findViewsByVersion("v1").get(0);

    assertThat(view.getItemProductName()).isNull();
    assertThat(view.getFinishedProductName()).isNull();
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -Dtest=ProductionPlanServiceTest test`
Expected: FAIL on missing DTO/service method.

- [ ] **Step 5: Commit**

```bash
git add aps-system/src/test/java/com/aps/service/ProductionPlanServiceTest.java
git commit -m "test: add production plan enrichment tests"
```

### Task 3: Implement Part Master Entity, Repository, and Service

**Files:**
- Create: `aps-system/src/main/java/com/aps/entity/PartMaster.java`
- Create: `aps-system/src/main/java/com/aps/repository/PartMasterRepository.java`
- Create: `aps-system/src/main/java/com/aps/service/PartMasterService.java`
- Modify: `aps-system/src/main/resources/schema.sql`
- Modify: `aps-system/src/main/resources/schema-current.sql`
- Modify: `aps-system/src/main/resources/data-seed.sql`
- Modify: `project.md`

- [ ] **Step 1: Add the JPA entity**

```java
@Entity
@Table(name = "t_part_master", uniqueConstraints = @UniqueConstraint(columnNames = "part_no"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "part_no", length = 50, nullable = false)
    private String partNo;

    @Column(name = "product_name", length = 200, nullable = false)
    private String productName;

    @Column(name = "product_no", length = 100, nullable = false)
    private String productNo;

    @Column(name = "project_name", length = 100, nullable = false)
    private String projectName;
}
```

- [ ] **Step 2: Add the repository**

```java
@Repository
public interface PartMasterRepository extends JpaRepository<PartMaster, Long> {

    Optional<PartMaster> findByPartNo(String partNo);

    List<PartMaster> findByPartNoIn(Collection<String> partNos);

    boolean existsByPartNo(String partNo);
}
```

- [ ] **Step 3: Add the service**

```java
@Service
@Transactional
public class PartMasterService {

    @Autowired
    private PartMasterRepository repository;

    public List<PartMaster> findAll() {
        return repository.findAll();
    }

    public PartMaster findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PartMaster not found: " + id));
    }

    public PartMaster findByPartNo(String partNo) {
        return repository.findByPartNo(partNo)
                .orElseThrow(() -> new IllegalArgumentException("PartMaster not found: " + partNo));
    }

    public PartMaster save(PartMaster entity) {
        if (repository.existsByPartNo(entity.getPartNo())) {
            throw new IllegalArgumentException("partNo already exists: " + entity.getPartNo());
        }
        return repository.save(entity);
    }

    public PartMaster update(Long id, PartMaster entity) {
        PartMaster existing = findById(id);
        if (!existing.getPartNo().equals(entity.getPartNo()) && repository.existsByPartNo(entity.getPartNo())) {
            throw new IllegalArgumentException("partNo already exists: " + entity.getPartNo());
        }
        existing.setPartNo(entity.getPartNo());
        existing.setProductName(entity.getProductName());
        existing.setProductNo(entity.getProductNo());
        existing.setProjectName(entity.getProjectName());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
```

- [ ] **Step 4: Add schema and seed data**

Add the table definition / seed rows:

```sql
CREATE TABLE IF NOT EXISTS t_part_master (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  part_no VARCHAR(50) NOT NULL,
  product_name VARCHAR(200) NOT NULL,
  product_no VARCHAR(100) NOT NULL,
  project_name VARCHAR(100) NOT NULL,
  UNIQUE KEY uk_part_master_part_no (part_no)
);
```

Example seed rows:

```sql
INSERT IGNORE INTO t_part_master (part_no, product_name, product_no, project_name) VALUES
('11201A012', '总成A', 'P-11201A012', '项目A'),
('21201A012', '半成品A', 'P-21201A012', '项目A');
```

- [ ] **Step 5: Update `project.md`**

Add `PartMaster` and `PartMasterController` to the project inventory.

- [ ] **Step 6: Run tests to verify green**

Run: `mvn -Dtest=PartMasterControllerTest,PartMasterServiceTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add aps-system/src/main/java/com/aps/entity/PartMaster.java aps-system/src/main/java/com/aps/repository/PartMasterRepository.java aps-system/src/main/java/com/aps/service/PartMasterService.java aps-system/src/main/resources/schema.sql aps-system/src/main/resources/schema-current.sql aps-system/src/main/resources/data-seed.sql project.md
git commit -m "feat: add part master domain model"
```

### Task 4: Implement Part Master Controller

**Files:**
- Create: `aps-system/src/main/java/com/aps/controller/PartMasterController.java`

- [ ] **Step 1: Add the controller**

```java
@RestController
@RequestMapping("/api/part-master")
@CrossOrigin
public class PartMasterController {

    @Autowired
    private PartMasterService service;

    @GetMapping
    public ApiResponse<List<PartMaster>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<PartMaster> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @GetMapping("/by-part-no/{partNo}")
    public ApiResponse<PartMaster> findByPartNo(@PathVariable String partNo) {
        return ApiResponse.success(service.findByPartNo(partNo));
    }

    @PostMapping
    public ApiResponse<PartMaster> create(@RequestBody PartMaster entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<PartMaster> update(@PathVariable Long id, @RequestBody PartMaster entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 2: Run controller tests**

Run: `mvn -Dtest=PartMasterControllerTest test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add aps-system/src/main/java/com/aps/controller/PartMasterController.java
git commit -m "feat: add part master api"
```

### Task 5: Implement Production Plan View DTO and Enrichment

**Files:**
- Create: `aps-system/src/main/java/com/aps/dto/ProductionPlanView.java`
- Modify: `aps-system/src/main/java/com/aps/service/ProductionPlanService.java`
- Modify: `aps-system/src/main/java/com/aps/controller/ProductionPlanController.java`

- [ ] **Step 1: Add the DTO**

```java
@Data
public class ProductionPlanView {
    private Long id;
    private String finishedProductCode;
    private String itemCode;
    private Integer yearMonth;
    private String process;
    private String equipment;
    private String manufacturingDepartment;
    private String manufacturingUnit;
    private Integer moldCavity;
    private Double cycleTime;
    private Double staffCount;
    private Double taktTime;
    private Double currentInventory;
    private Double forecast;
    private Double safetyDays;
    private Double operatingDays;
    private Double scrapRate;
    private String isProduce;
    private Double planQty;
    private String version;
    private String itemProductName;
    private String itemProductNo;
    private String itemProjectName;
    private String finishedProductName;
    private String finishedProductNo;
    private String finishedProjectName;
}
```

- [ ] **Step 2: Inject `PartMasterRepository` into `ProductionPlanService`**

Add batch enrichment:

```java
@Autowired
private PartMasterRepository partMasterRepository;

public List<ProductionPlanView> findViewsByVersion(String version) {
    return toViews(repository.findByVersion(version));
}

public List<ProductionPlanView> findAllViews() {
    return toViews(repository.findAll());
}
```

- [ ] **Step 3: Add the batch mapping helper**

```java
private List<ProductionPlanView> toViews(List<ProductionPlan> plans) {
    Set<String> partNos = plans.stream()
            .flatMap(p -> Stream.of(p.getItemCode(), p.getFinishedProductCode()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<String, PartMaster> masterMap = partMasterRepository.findByPartNoIn(partNos).stream()
            .collect(Collectors.toMap(PartMaster::getPartNo, Function.identity()));

    return plans.stream().map(plan -> {
        ProductionPlanView view = new ProductionPlanView();
        BeanUtils.copyProperties(plan, view);

        PartMaster item = masterMap.get(plan.getItemCode());
        if (item != null) {
            view.setItemProductName(item.getProductName());
            view.setItemProductNo(item.getProductNo());
            view.setItemProjectName(item.getProjectName());
        }

        PartMaster finished = masterMap.get(plan.getFinishedProductCode());
        if (finished != null) {
            view.setFinishedProductName(finished.getProductName());
            view.setFinishedProductNo(finished.getProductNo());
            view.setFinishedProjectName(finished.getProjectName());
        }
        return view;
    }).collect(Collectors.toList());
}
```

- [ ] **Step 4: Switch controller endpoints to return views**

Update these endpoints:

```java
@GetMapping
public ApiResponse<List<ProductionPlanView>> findAll() {
    return ApiResponse.success(service.findAllViews());
}

@GetMapping("/by-version/{version}")
public ApiResponse<List<ProductionPlanView>> findByVersion(@PathVariable String version) {
    return ApiResponse.success(service.findViewsByVersion(version));
}
```

If the controller also exposes by-period / by-product endpoints, convert them through the same `toViews(...)` path.

- [ ] **Step 5: Run tests**

Run: `mvn -Dtest=ProductionPlanServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add aps-system/src/main/java/com/aps/dto/ProductionPlanView.java aps-system/src/main/java/com/aps/service/ProductionPlanService.java aps-system/src/main/java/com/aps/controller/ProductionPlanController.java
git commit -m "feat: enrich production plan queries with part master"
```

### Task 6: Final Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-05-24-part-master-design.md` (only if implementation drift requires clarification)

- [ ] **Step 1: Run focused tests**

Run:
`mvn -Dtest=PartMasterControllerTest,PartMasterServiceTest,ProductionPlanServiceTest test`

Expected: PASS

- [ ] **Step 2: Run broader API regression**

Run:
`mvn -Dtest=ProductionPlanControllerTest,PlanCalculationServiceTest test`

Expected: PASS, confirming enriched query responses do not break existing plan calculation behavior.

- [ ] **Step 3: Inspect final diff**

Run:
`git diff --stat`

Expected: only part master domain, production plan query, schema/seed, and supporting tests/docs changed.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/plans/2026-05-24-part-master.md
git commit -m "chore: finalize part master rollout plan"
```
