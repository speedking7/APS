package com.aps.service;

import com.aps.entity.Bom;
import com.aps.entity.Demand;
import com.aps.entity.InventoryCount;
import com.aps.entity.OperatingDays;
import com.aps.entity.SafetyStock;
import com.aps.repository.BomRepository;
import com.aps.repository.DemandRepository;
import com.aps.repository.InventoryCountRepository;
import com.aps.repository.OperatingDaysRepository;
import com.aps.repository.SafetyStockRepository;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelImportServiceTest {

    @InjectMocks
    private ExcelImportService service;

    @Mock
    private DemandRepository demandRepository;

    @Mock
    private BomRepository bomRepository;

    @Mock
    private InventoryCountRepository inventoryCountRepository;

    @Mock
    private SafetyStockRepository safetyStockRepository;

    @Mock
    private OperatingDaysRepository operatingDaysRepository;

    @Captor
    private ArgumentCaptor<List<Demand>> demandsCaptor;

    @Captor
    private ArgumentCaptor<List<Bom>> bomsCaptor;

    @Captor
    private ArgumentCaptor<List<InventoryCount>> inventoryCaptor;

    @Captor
    private ArgumentCaptor<List<SafetyStock>> safetyCaptor;

    private byte[] buildWorkbookWithBomRow(
            String rootProductCode,
            String manufacturingDepartment,
            String manufacturingUnit,
            String partAttribute) throws Exception {
        return buildWorkbookWithBomRowInTemplateOrder(
                rootProductCode,
                manufacturingDepartment,
                manufacturingUnit,
                partAttribute,
                "v1");
    }

    private byte[] buildWorkbookWithBomRowInTemplateOrder(
            String rootProductCode,
            String manufacturingDepartment,
            String manufacturingUnit,
            String partAttribute,
            String version) throws Exception {
        Workbook wb = new XSSFWorkbook();

        Sheet demandSheet = wb.createSheet("完成品入库需求数");
        addRow(demandSheet, 0);
        addRow(demandSheet, 1);
        addRow(demandSheet, 2, "AAA", "P001", 202601, 100.0, 10.0, 5.0, 90.0, "v1");

        Sheet bomSheet = wb.createSheet("BOM");
        addRow(bomSheet, 0);
        addRow(bomSheet, 1);
        addRow(bomSheet, 2,
                rootProductCode, "P001", "C001", 1.0, "STAMP", "EQ-01",
                manufacturingDepartment, manufacturingUnit,
                2, 30.0, 1.0, 15.0, 0.02, partAttribute, version);

        Sheet inventorySheet = wb.createSheet("半成品期末盘点数");
        addRow(inventorySheet, 0);
        addRow(inventorySheet, 1);
        addRow(inventorySheet, 2, "C001", 202512, 20.0, "v1");

        Sheet safetySheet = wb.createSheet("半成品安全库存");
        addRow(safetySheet, 0);
        addRow(safetySheet, 1);
        addRow(safetySheet, 2, "C001", 202601, 10.0, 3.0, 15.0, "v1");

        Sheet opDaysSheet = wb.createSheet("稼动天数");
        addRow(opDaysSheet, 0);
        addRow(opDaysSheet, 1);
        addRow(opDaysSheet, 2, 202601, 31.0, 22.0, 8.0, 1.0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    private void addRow(Sheet sheet, int rowNum, Object... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value == null) {
                continue;
            }
            if (value instanceof String) {
                row.createCell(i).setCellValue((String) value);
            } else if (value instanceof Integer) {
                row.createCell(i).setCellValue((Integer) value);
            } else if (value instanceof Double) {
                row.createCell(i).setCellValue((Double) value);
            }
        }
    }

    @Test
    void importFromExcel_parsesCurrentSheetsAndCountsRows() throws Exception {
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());

        ImportResult result = service.importFromExcel(
                new ByteArrayInputStream(buildWorkbookWithBomRow("P001", "制造一部", "单元A", "采购件")));

        assertThat(result.getDemandCount()).isEqualTo(1);
        assertThat(result.getBomCount()).isEqualTo(1);
        assertThat(result.getInventoryCountCount()).isEqualTo(1);
        assertThat(result.getSafetyStockCount()).isEqualTo(1);
        assertThat(result.getOperatingDaysCount()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void importFromExcel_savesRootProductCodeAndManufacturingFields() throws Exception {
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());

        service.importFromExcel(new ByteArrayInputStream(buildWorkbookWithBomRow("P001", "制造一部", "单元A", "采购件")));

        verify(bomRepository).saveAll(bomsCaptor.capture());
        Bom saved = bomsCaptor.getValue().get(0);
        assertThat(saved.getRootProductCode()).isEqualTo("P001");
        assertThat(saved.getManufacturingDepartment()).isEqualTo("制造一部");
        assertThat(saved.getManufacturingUnit()).isEqualTo("单元A");
        assertThat(saved.getPartAttribute()).isEqualTo("采购件");
    }

    @Test
    void importFromExcel_bomColumnsFollowTemplateOrderForPartAttributeAndVersion() throws Exception {
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());

        service.importFromExcel(new ByteArrayInputStream(
                buildWorkbookWithBomRowInTemplateOrder("P001", "制造一部", "单元A", "采购件", "v-template")));

        verify(bomRepository).saveAll(bomsCaptor.capture());
        Bom saved = bomsCaptor.getValue().get(0);
        assertThat(saved.getPartAttribute()).isEqualTo("采购件");
        assertThat(saved.getVersion()).isEqualTo("v-template");
    }

    @Test
    void importFromExcel_missingRootProductCode_isRejected() throws Exception {
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());

        ImportResult result = service.importFromExcel(
                new ByteArrayInputStream(buildWorkbookWithBomRow(null, "制造一部", "单元A", "采购件")));

        assertThat(result.getBomCount()).isEqualTo(0);
        assertThat(result.getErrors()).anyMatch(msg -> msg.contains("根完成品编码无效"));
        verify(bomRepository, never()).saveAll(any());
    }

    @Test
    void importFromExcel_missingManufacturingDepartment_isRejected() throws Exception {
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());

        ImportResult result = service.importFromExcel(
                new ByteArrayInputStream(buildWorkbookWithBomRow("P001", null, "单元A", "采购件")));

        assertThat(result.getBomCount()).isEqualTo(0);
        assertThat(result.getErrors()).anyMatch(msg -> msg.contains("制造部门必填"));
        verify(bomRepository, never()).saveAll(any());
    }

    @Test
    void importFromExcel_missingManufacturingUnit_isRejected() throws Exception {
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());

        ImportResult result = service.importFromExcel(
                new ByteArrayInputStream(buildWorkbookWithBomRow("P001", "制造一部", null, "采购件")));

        assertThat(result.getBomCount()).isEqualTo(0);
        assertThat(result.getErrors()).anyMatch(msg -> msg.contains("制造单元必填"));
        verify(bomRepository, never()).saveAll(any());
    }

    @Test
    void importFromExcel_savesOtherSheetsUsingCurrentRepositories() throws Exception {
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());

        service.importFromExcel(new ByteArrayInputStream(buildWorkbookWithBomRow("P001", "制造一部", "单元A", "采购件")));

        verify(demandRepository).saveAll(demandsCaptor.capture());
        verify(inventoryCountRepository).saveAll(inventoryCaptor.capture());
        verify(safetyStockRepository).saveAll(safetyCaptor.capture());

        Demand demand = demandsCaptor.getValue().get(0);
        assertThat(demand.getCustomer()).isEqualTo("AAA");
        assertThat(demand.getItemCode()).isEqualTo("P001");
        assertThat(demand.getVersion()).isEqualTo("v1");

        InventoryCount inventoryCount = inventoryCaptor.getValue().get(0);
        assertThat(inventoryCount.getItemCode()).isEqualTo("C001");
        assertThat(inventoryCount.getYearMonth()).isEqualTo(202512);

        SafetyStock safetyStock = safetyCaptor.getValue().get(0);
        assertThat(safetyStock.getItemCode()).isEqualTo("C001");
        assertThat(safetyStock.getYearMonth()).isEqualTo(202601);
        assertThat(safetyStock.getSafetyDays()).isEqualTo(3.0);
    }

    @Test
    void importFromExcel_upsertsOperatingDays() throws Exception {
        OperatingDays existing = new OperatingDays(1L, 202601, 30.0, 20.0, 8.0, 2.0);
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.of(existing));

        service.importFromExcel(new ByteArrayInputStream(buildWorkbookWithBomRow("P001", "制造一部", "单元A", "采购件")));

        assertThat(existing.getTotalDays()).isEqualTo(31.0);
        assertThat(existing.getWorkDays()).isEqualTo(22.0);
        assertThat(existing.getWeekendDays()).isEqualTo(8.0);
        assertThat(existing.getHolidayDays()).isEqualTo(1.0);
        verify(operatingDaysRepository).save(existing);
    }

    @Test
    void bomTemplate_firstRowTitle_isHorizontallyCentered() throws Exception {
        try (InputStream inputStream = ExcelImportServiceTest.class.getResourceAsStream("/static/template-bom.xlsx")) {
            assertThat(inputStream).isNotNull();

            try (Workbook workbook = new XSSFWorkbook(inputStream)) {
                Sheet sheet = workbook.getSheet("BOM");
                assertThat(sheet).isNotNull();
                assertThat(sheet.getRow(0).getCell(0).getCellStyle().getAlignment())
                        .isEqualTo(HorizontalAlignment.CENTER);
                assertThat(sheet.getMergedRegions()).anySatisfy(range -> {
                    assertThat(range.formatAsString()).isEqualTo("A1:O1");
                });
                assertThat(sheet.getRow(1).getCell(12).getStringCellValue()).isEqualTo("报废率");
                assertThat(sheet.getRow(1).getCell(13).getStringCellValue()).isEqualTo("子零件属性");
                assertThat(sheet.getRow(1).getCell(14).getStringCellValue()).isEqualTo("版本号");
                assertThat(sheet.getRow(1).getCell(12).getCellStyle().getFillForegroundColor())
                        .isEqualTo(sheet.getRow(1).getCell(13).getCellStyle().getFillForegroundColor());
                assertThat(sheet.getRow(1).getCell(12).getCellStyle().getFillPattern())
                        .isEqualTo(sheet.getRow(1).getCell(13).getCellStyle().getFillPattern());
                assertThat(sheet.getRow(1).getCell(12).getCellStyle().getFontIndexAsInt())
                        .isEqualTo(sheet.getRow(1).getCell(13).getCellStyle().getFontIndexAsInt());
            }
        }
    }
}
