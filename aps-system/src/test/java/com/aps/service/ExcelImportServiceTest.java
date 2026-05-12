package com.aps.service;

import com.aps.entity.*;
import com.aps.repository.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExcelImportService 单元测试
 *
 * 验证 6 张 Sheet 的解析逻辑：
 *   预测（仅完成品）→ Forecast
 *   报废率          → ScrapRate
 *   库存天数        → InventoryDays
 *   稼动天数        → OperatingDays
 *   BOM             → Bom
 *   盘点数          → InventoryCount
 */
@ExtendWith(MockitoExtension.class)
class ExcelImportServiceTest {

    @InjectMocks  private ExcelImportService service;

    @Mock private ForecastRepository        forecastRepository;
    @Mock private ScrapRateRepository       scrapRateRepository;
    @Mock private InventoryDaysRepository   inventoryDaysRepository;
    @Mock private OperatingDaysRepository   operatingDaysRepository;
    @Mock private BomRepository             bomRepository;
    @Mock private InventoryCountRepository  inventoryCountRepository;

    @Captor private ArgumentCaptor<List<Forecast>>       forecasts;
    @Captor private ArgumentCaptor<List<ScrapRate>>      scrapRates;
    @Captor private ArgumentCaptor<List<InventoryDays>>  invDays;
    @Captor private ArgumentCaptor<List<OperatingDays>>  opDays;
    @Captor private ArgumentCaptor<List<Bom>>            boms;
    @Captor private ArgumentCaptor<List<InventoryCount>> invCounts;

    // -------------------------------------------------------------------------
    // Helper: build a complete workbook matching the template structure
    // -------------------------------------------------------------------------
    private byte[] buildWorkbook() throws Exception {
        Workbook wb = new XSSFWorkbook();

        // 预测（仅完成品）: 存货编码, 年月, 数量, 删除重写标记
        Sheet forecast = wb.createSheet("预测（仅完成品）");
        addRow(forecast, 0, "存货编码", "年月", "数量", "删除重写标记");
        addRow(forecast, 1, "P001", 202601, 100.0, "分类1");
        addRow(forecast, 2, "P001", 202602, 110.0, "分类1");

        // 报废率: 存货编码, 报废率
        Sheet scrap = wb.createSheet("报废率");
        addRow(scrap, 0, "存货编码", "报废率");
        addRow(scrap, 1, "P001", 0.01);
        addRow(scrap, 2, "C001", 0.02);

        // 库存天数: 存货编码, 安全天数, 最大天数
        Sheet invDaySheet = wb.createSheet("库存天数");
        addRow(invDaySheet, 0, "存货编码", "安全天数", "最大天数");
        addRow(invDaySheet, 1, "P001", 3.0, 15.0);

        // 稼动天数: 年月, 天数
        Sheet opDaySheet = wb.createSheet("稼动天数");
        addRow(opDaySheet, 0, "年月", "天数");
        addRow(opDaySheet, 1, 202601, 22.0);
        addRow(opDaySheet, 2, 202602, 20.0);

        // BOM: 父零件, 子零件, 用量, 工序, 设备, 模腔数, 制造周期, 持台人数, 单件节拍
        Sheet bomSheet = wb.createSheet("BOM");
        addRow(bomSheet, 0, "父零件", "子零件", "用量", "工序", "设备",
               "模腔数/取数（pcs）", "制造周期（S）", "持台人数（人）", "单件节拍（S）");
        addRow(bomSheet, 1, "P001", "C001", 2.0, "CNC", "EQ-01", 1, 30.0, 2.0, 15.0);
        addRow(bomSheet, 2, "C001", null, null, "WELD", "EQ-02", 2, 20.0, 1.0, 10.0);

        // 盘点数: 存货编码, 年月（底）, 可用量, 删除重写标记
        Sheet invCountSheet = wb.createSheet("盘点数");
        addRow(invCountSheet, 0, "存货编码", "年月（底）", "可用量", "删除重写标记");
        addRow(invCountSheet, 1, "P001", 202512, 50.0, "分类1");
        addRow(invCountSheet, 2, "C001", 202512, 30.0, "分类1");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    private void addRow(Sheet sheet, int rowIdx, Object... values) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object v = values[i];
            if (v == null) {
                cell.setBlank();
            } else if (v instanceof String) {
                cell.setCellValue((String) v);
            } else if (v instanceof Double) {
                cell.setCellValue((Double) v);
            } else if (v instanceof Integer) {
                cell.setCellValue((Integer) v);
            }
        }
    }

    // =========================================================================
    // 1. 导入结果统计
    // =========================================================================

    @Test
    void import_returnsCorrectRowCounts() throws Exception {
        byte[] bytes = buildWorkbook();
        ImportResult result = service.importFromExcel(new ByteArrayInputStream(bytes));

        assertThat(result.getOperatingDaysCount()).isEqualTo(2);
        assertThat(result.getBomCount()).isEqualTo(2);
        assertThat(result.getInventoryCountCount()).isEqualTo(2);
    }

    // =========================================================================
    // 2. 预测解析
    // =========================================================================

    @Test
    void import_parsesForecastSheet_correctly() throws Exception {
        service.importFromExcel(new ByteArrayInputStream(buildWorkbook()));

        verify(forecastRepository).saveAll(forecasts.capture());
        List<Forecast> saved = forecasts.getValue();
        assertThat(saved).hasSize(2);
        Forecast f = saved.get(0);
        assertThat(f.getItemCode()).isEqualTo("P001");
        assertThat(f.getYearMonth()).isEqualTo(202601);
        assertThat(f.getQuantity()).isEqualTo(100.0);
    }

    // =========================================================================
    // 3. 报废率解析
    // =========================================================================

    @Test
    void import_parsesScrapRateSheet_correctly() throws Exception {
        service.importFromExcel(new ByteArrayInputStream(buildWorkbook()));

        verify(scrapRateRepository).saveAll(scrapRates.capture());
        List<ScrapRate> saved = scrapRates.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getItemCode()).isEqualTo("P001");
        assertThat(saved.get(0).getScrapRate()).isEqualTo(0.01);
        assertThat(saved.get(1).getItemCode()).isEqualTo("C001");
    }

    // =========================================================================
    // 4. 库存天数解析
    // =========================================================================

    @Test
    void import_parsesInventoryDaysSheet_correctly() throws Exception {
        service.importFromExcel(new ByteArrayInputStream(buildWorkbook()));

        verify(inventoryDaysRepository).saveAll(invDays.capture());
        List<InventoryDays> saved = invDays.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getItemCode()).isEqualTo("P001");
        assertThat(saved.get(0).getSafetyDays()).isEqualTo(3.0);
        assertThat(saved.get(0).getMaxDays()).isEqualTo(15.0);
    }

    // =========================================================================
    // 5. 稼动天数解析
    // =========================================================================

    @Test
    void import_parsesOperatingDaysSheet_correctly() throws Exception {
        service.importFromExcel(new ByteArrayInputStream(buildWorkbook()));

        verify(operatingDaysRepository).saveAll(opDays.capture());
        List<OperatingDays> saved = opDays.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getYearMonth()).isEqualTo(202601);
        assertThat(saved.get(0).getWorkDays()).isEqualTo(22.0);
    }

    // =========================================================================
    // 6. BOM 解析
    // =========================================================================

    @Test
    void import_parsesBomSheet_correctly() throws Exception {
        service.importFromExcel(new ByteArrayInputStream(buildWorkbook()));

        verify(bomRepository).saveAll(boms.capture());
        List<Bom> saved = boms.getValue();
        assertThat(saved).hasSize(2);

        Bom b = saved.get(0);
        assertThat(b.getParentCode()).isEqualTo("P001");
        assertThat(b.getChildCode()).isEqualTo("C001");
        assertThat(b.getUsageQty()).isEqualTo(2.0);
        assertThat(b.getProcess()).isEqualTo("CNC");
        assertThat(b.getEquipment()).isEqualTo("EQ-01");
        assertThat(b.getMoldCavity()).isEqualTo(1);
        assertThat(b.getCycleTime()).isEqualTo(30.0);
        assertThat(b.getStaffCount()).isEqualTo(2.0);
        assertThat(b.getTaktTime()).isEqualTo(15.0);
    }

    @Test
    void import_bomLeafNode_childCodeIsNull() throws Exception {
        service.importFromExcel(new ByteArrayInputStream(buildWorkbook()));

        verify(bomRepository).saveAll(boms.capture());
        Bom leaf = boms.getValue().get(1);
        assertThat(leaf.getParentCode()).isEqualTo("C001");
        assertThat(leaf.getChildCode()).isNull();
    }

    // =========================================================================
    // 7. 盘点数解析
    // =========================================================================

    @Test
    void import_parsesInventoryCountSheet_correctly() throws Exception {
        service.importFromExcel(new ByteArrayInputStream(buildWorkbook()));

        verify(inventoryCountRepository).saveAll(invCounts.capture());
        List<InventoryCount> saved = invCounts.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getItemCode()).isEqualTo("P001");
        assertThat(saved.get(0).getYearMonth()).isEqualTo(202512);
        assertThat(saved.get(0).getAvailableQty()).isEqualTo(50.0);
    }

    // =========================================================================
    // 8. 覆盖模式：导入前清空各表
    // =========================================================================

    @Test
    void import_clearsAllTablesBeforeSaving() throws Exception {
        service.importFromExcel(new ByteArrayInputStream(buildWorkbook()));

        verify(forecastRepository).deleteAllInBatch();
        verify(scrapRateRepository).deleteAllInBatch();
        verify(inventoryDaysRepository).deleteAllInBatch();
        verify(operatingDaysRepository).deleteAllInBatch();
        verify(bomRepository).deleteAllInBatch();
        verify(inventoryCountRepository).deleteAllInBatch();
    }
}
