package com.aps.service;

import com.aps.dto.ProductionPlanView;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkforceDetailServiceTest {

    @InjectMocks
    private WorkforceDetailService service;

    @Mock
    private ProductionPlanService productionPlanService;

    @Mock
    private SharedMoldRuleService sharedMoldRuleService;

    private ProductionPlanView makePlan(
            String version,
            String manufacturingDepartment,
            String manufacturingUnit,
            String finishedProductCode,
            String itemCode,
            Integer yearMonth,
            Double planQty,
            String process,
            Double staffCount,
            Double taktTime
    ) {
        ProductionPlanView plan = new ProductionPlanView();
        plan.setVersion(version);
        plan.setManufacturingDepartment(manufacturingDepartment);
        plan.setManufacturingUnit(manufacturingUnit);
        plan.setFinishedProductCode(finishedProductCode);
        plan.setItemCode(itemCode);
        plan.setYearMonth(yearMonth);
        plan.setPlanQty(planQty);
        plan.setProcess(process);
        plan.setStaffCount(staffCount);
        plan.setTaktTime(taktTime);
        return plan;
    }

    @Test
    void findDetailsByVersion_returnsDetailRowsMatchingCurrentPageFormula() {
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(List.of(
                makePlan("v1", "制造一部", "单元A", "P-100", "P-100", 202601, 120.0, "冲压", 2.0, 30.0)
        ));

        List<WorkforceDetailRow> result = service.findDetailsByVersion("v1");

        assertThat(result).hasSize(1);
        WorkforceDetailRow row = result.get(0);
        assertThat(row.getManufacturingDepartment()).isEqualTo("制造一部");
        assertThat(row.getManufacturingUnit()).isEqualTo("单元A");
        assertThat(row.getProductCode()).isEqualTo("P-100");
        assertThat(row.getYearMonth()).isEqualTo(202601);
        assertThat(row.getPlanQty()).isCloseTo(120.0, within(0.001));
        assertThat(row.getProcess()).isEqualTo("冲压");
        assertThat(row.getStaffCount()).isCloseTo(2.0, within(0.000001));
        assertThat(row.getTaktTime()).isCloseTo(30.0, within(0.000001));
        assertThat(row.getRequiredSeconds()).isCloseTo(120.0 * 2.0 * 30.0, within(0.000001));
        assertThat(row.getRequiredHours()).isCloseTo((120.0 * 2.0 * 30.0) / 3600.0, within(0.000001));
    }

    @Test
    void missingFields_useFallbacksAndZerosLikeCurrentPage() {
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(List.of(
                makePlan("v1", null, null, null, null, 202601, 100.0, "焊接", null, null)
        ));

        List<WorkforceDetailRow> result = service.findDetailsByVersion("v1");

        assertThat(result).hasSize(1);
        WorkforceDetailRow row = result.get(0);
        assertThat(row.getManufacturingDepartment()).isEqualTo("—");
        assertThat(row.getManufacturingUnit()).isEqualTo("—");
        assertThat(row.getProductCode()).isEqualTo("—");
        assertThat(row.getStaffCount()).isEqualTo(0.0);
        assertThat(row.getTaktTime()).isEqualTo(0.0);
        assertThat(row.getRequiredSeconds()).isEqualTo(0.0);
        assertThat(row.getRequiredHours()).isEqualTo(0.0);
    }

    @Test
    void rowsWithoutYearMonthOrProcess_areExcluded() {
        ProductionPlanView missingMonth = makePlan("v1", "制造一部", "单元A", "P-1", "P-1", null, 100.0, "冲压", 2.0, 30.0);
        ProductionPlanView missingProcess = makePlan("v1", "制造一部", "单元A", "P-2", "P-2", 202601, 100.0, null, 2.0, 30.0);
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(List.of(missingMonth, missingProcess));

        List<WorkforceDetailRow> result = service.findDetailsByVersion("v1");

        assertThat(result).isEmpty();
    }

    @Test
    void resultIsSortedLikeCurrentPage() {
        ProductionPlanView row1 = makePlan("v1", "制造二部", "单元B", "B-200", "B-200", 202602, 100.0, "装配", 1.0, 10.0);
        ProductionPlanView row2 = makePlan("v1", "制造一部", "单元A", "A-100", "A-100", 202601, 100.0, "冲压", 1.0, 10.0);
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(List.of(row1, row2));

        List<WorkforceDetailRow> result = service.findDetailsByVersion("v1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getYearMonth()).isEqualTo(202601);
        assertThat(result.get(0).getManufacturingDepartment()).isEqualTo("制造一部");
    }

    @Test
    void emptyVersionResult_returnsEmptyList() {
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(Collections.emptyList());

        assertThat(service.findDetailsByVersion("v1")).isEmpty();
    }

    @Test
    void repositoryIsQueriedByVersion() {
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(Collections.emptyList());

        service.findDetailsByVersion("v1");

        verify(productionPlanService).findViewsByVersion("v1");
        verify(productionPlanService, never()).findAllViews();
    }

    @Test
    void sharedMoldPair_keepsBothRowsButSuppressesSmallerSelfProductDemand() {
        when(sharedMoldRuleService.findEnabledRules()).thenReturn(List.of(
                new com.aps.entity.SharedMoldRule(1L, "203000324D", "203000326D", null, null, true, null),
                new com.aps.entity.SharedMoldRule(2L, "203000328D", "203000330D", null, null, true, null)
        ));
        ProductionPlanView smaller = makePlan("v1", "制造一部", "单元A", "203000324D", "203000324D", 202606, 80.0, "加饰注塑", 1.0, 20.0);
        ProductionPlanView larger = makePlan("v1", "制造一部", "单元A", "203000326D", "203000326D", 202606, 120.0, "加饰注塑", 1.0, 20.0);
        ProductionPlanView child = makePlan("v1", "制造一部", "单元A", "203000324D", "206100001D", 202606, 200.0, "热压", 1.0, 30.0);
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(List.of(smaller, larger, child));

        List<WorkforceDetailRow> result = service.findDetailsByVersion("v1");

        assertThat(result).hasSize(3);
        WorkforceDetailRow suppressed = result.stream()
                .filter(row -> "203000324D".equals(row.getProductCode()))
                .findFirst()
                .orElseThrow();
        WorkforceDetailRow active = result.stream()
                .filter(row -> "203000326D".equals(row.getProductCode()))
                .findFirst()
                .orElseThrow();
        assertThat(suppressed.getSharedMoldAdjusted()).isTrue();
        assertThat(suppressed.getSharedMoldSuppressed()).isTrue();
        assertThat(suppressed.getRequiredSeconds()).isEqualTo(0.0);
        assertThat(suppressed.getRequiredHours()).isEqualTo(0.0);
        assertThat(active.getSharedMoldSuppressed()).isFalse();
        assertThat(active.getRequiredSeconds()).isGreaterThan(0.0);
    }

    @Test
    void exportWorkbook_detail_writesDetailColumns() throws Exception {
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(List.of(
                makePlan("v1", "制造一部", "单元A", "P-100", "P-100", 202601, 120.0, "冲压", 2.0, 30.0)
        ));

        byte[] bytes = service.exportWorkbook("v1", null, null, null, null, null, "detail");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("工时分析");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("制造部门");
            assertThat(sheet.getRow(0).getCell(9).getStringCellValue()).isEqualTo("所需工时");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("制造一部");
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("冲压");
        }
    }

    @Test
    void exportWorkbook_summary_aggregatesHoursAndUsesSummaryColumns() throws Exception {
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(List.of(
                makePlan("v1", "制造一部", "单元A", "P-100", "P-100", 202601, 120.0, "冲压", 2.0, 30.0),
                makePlan("v1", "制造一部", "单元A", "P-200", "P-200", 202601, 60.0, "冲压", 1.0, 30.0)
        ));

        byte[] bytes = service.exportWorkbook("v1", null, null, null, null, null, "summary");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("工时分析汇总");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("制造部门");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("所需工时");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("制造一部");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("冲压");
            assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isCloseTo(2.5, within(0.000001));
        }
    }
}
