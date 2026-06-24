package com.aps.service;

import com.aps.dto.ProductionPlanView;
import com.aps.entity.Demand;
import com.aps.entity.PartMaster;
import com.aps.entity.ProductionPlan;
import com.aps.repository.DemandRepository;
import com.aps.repository.PartMasterRepository;
import com.aps.repository.ProductionPlanRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPlanServiceTest {

    @InjectMocks
    private ProductionPlanService service;

    @Mock
    private ProductionPlanRepository repository;

    @Mock
    private PartMasterRepository partMasterRepository;

    @Mock
    private DemandRepository demandRepository;

    @Test
    void findByVersion_enrichesItemAndFinishedProductAttributes() {
        ProductionPlan plan = new ProductionPlan();
        plan.setFinishedProductCode("FP001");
        plan.setItemCode("C001");
        plan.setVersion("v1");
        plan.setCalculatedAt(LocalDateTime.of(2026, 5, 26, 22, 0, 0));

        when(repository.findByVersion("v1")).thenReturn(List.of(plan));
        when(partMasterRepository.findByPartNoIn(Set.of("FP001", "C001")))
                .thenReturn(List.of(
                        new PartMaster(1L, "FP001", "整机", "FNO-1", "项目A"),
                        new PartMaster(2L, "C001", "支架", "CNO-1", "项目A")));

        List<ProductionPlanView> views = service.findViewsByVersion("v1");

        assertThat(views).hasSize(1);
        ProductionPlanView view = views.get(0);
        assertThat(view.getItemProductName()).isEqualTo("支架");
        assertThat(view.getItemProductNo()).isEqualTo("CNO-1");
        assertThat(view.getFinishedProductName()).isEqualTo("整机");
        assertThat(view.getFinishedProductNo()).isEqualTo("FNO-1");
        assertThat(view.getCalculatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 26, 22, 0, 0));
    }

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

    @Test
    void findByVersion_enrichesFinishedProductDemandFieldsForDetailModal() {
        ProductionPlan plan = new ProductionPlan();
        plan.setFinishedProductCode("FP001");
        plan.setItemCode("FP001");
        plan.setYearMonth(202607);
        plan.setCurrentInventory(615.0);
        plan.setForecast(0.0);
        plan.setPlanQty(0.0);
        plan.setVersion("v1");

        when(repository.findByVersion("v1")).thenReturn(List.of(plan));
        when(partMasterRepository.findByPartNoIn(Set.of("FP001"))).thenReturn(Collections.emptyList());
        when(demandRepository.findByVersion("v1")).thenReturn(List.of(
                new Demand(1L, "AAA", "FP001", 202606, 537.0, 1152.0, 120.0, 0.0, "v1"),
                new Demand(2L, "AAA", "FP001", 202607, 374.0, 0.0, 90.0, 464.0, "v1")
        ));

        ProductionPlanView view = service.findViewsByVersion("v1").get(0);

        assertThat(view.getDemandQty()).isEqualTo(374.0);
        assertThat(view.getMinSafetyStock()).isEqualTo(90.0);
        assertThat(view.getEndingInventory()).isEqualTo(0.0);
        assertThat(view.getPreviousPeriodEndingInventory()).isEqualTo(615.0);
    }

    @Test
    void exportWorkbook_writesHeadersAndFilteredRows() throws Exception {
        ProductionPlan january = new ProductionPlan();
        january.setFinishedProductCode("FP001");
        january.setItemCode("C001");
        january.setYearMonth(202601);
        january.setProcess("冲压");
        january.setEquipment("EQ-01");
        january.setManufacturingDepartment("制造一部");
        january.setManufacturingUnit("单元A");
        january.setForecast(100.0);
        january.setPlanQty(120.0);
        january.setScrapRate(0.05);
        january.setSafetyDays(3.0);
        january.setCurrentInventory(20.0);
        january.setVersion("v1");
        january.setIsProduce("Y");
        january.setCalculatedAt(LocalDateTime.of(2026, 6, 20, 12, 0, 0));

        ProductionPlan february = new ProductionPlan();
        february.setFinishedProductCode("FP001");
        february.setItemCode("C002");
        february.setYearMonth(202602);
        february.setVersion("v2");

        when(repository.findAll()).thenReturn(List.of(january, february));
        when(partMasterRepository.findByPartNoIn(Set.of("FP001", "C001", "C002")))
                .thenReturn(List.of(
                        new PartMaster(1L, "FP001", "成品A", "F-001", "项目A"),
                        new PartMaster(2L, "C001", "子件A", "C-001", "项目A")));

        byte[] bytes = service.exportWorkbook("v1", 202601, "FP001", "C001");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("计划结果");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("完成品");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("自制件编码");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("FP001");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("C001");
            assertThat(sheet.getRow(1).getCell(15).getStringCellValue()).isEqualTo("v1");
        }
    }
}
