package com.aps.service;

import com.aps.entity.*;
import com.aps.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 全量导入服务
 *
 * 导入策略：覆盖模式，先清空各表再写入。
 * Sheet 顺序与模板一致：
 *   预测（仅完成品）/ 报废率 / 库存天数 / 稼动天数 / BOM / 盘点数
 */
@Service
@Transactional
public class ExcelImportService {

    @Autowired private ForecastRepository       forecastRepository;
    @Autowired private ScrapRateRepository      scrapRateRepository;
    @Autowired private InventoryDaysRepository  inventoryDaysRepository;
    @Autowired private OperatingDaysRepository  operatingDaysRepository;
    @Autowired private BomRepository            bomRepository;
    @Autowired private InventoryCountRepository inventoryCountRepository;

    public ImportResult importFromExcel(InputStream inputStream) throws IOException {
        ImportResult result = new ImportResult();

        try (Workbook wb = new XSSFWorkbook(inputStream)) {
            // 1. 清空所有输入表
            forecastRepository.deleteAllInBatch();
            scrapRateRepository.deleteAllInBatch();
            inventoryDaysRepository.deleteAllInBatch();
            operatingDaysRepository.deleteAllInBatch();
            bomRepository.deleteAllInBatch();
            inventoryCountRepository.deleteAllInBatch();

            // 2. 按 sheet 解析并保存
            List<Forecast>       forecasts  = parseForecasts(wb.getSheet("预测（仅完成品）"));
            List<ScrapRate>      scrapRates = parseScrapRates(wb.getSheet("报废率"));
            List<InventoryDays>  invDays    = parseInventoryDays(wb.getSheet("库存天数"));
            List<OperatingDays>  opDays     = parseOperatingDays(wb.getSheet("稼动天数"));
            List<Bom>            boms       = parseBoms(wb.getSheet("BOM"));
            List<InventoryCount> invCounts  = parseInventoryCounts(wb.getSheet("盘点数"));

            forecastRepository.saveAll(forecasts);
            scrapRateRepository.saveAll(scrapRates);
            inventoryDaysRepository.saveAll(invDays);
            operatingDaysRepository.saveAll(opDays);
            bomRepository.saveAll(boms);
            inventoryCountRepository.saveAll(invCounts);

            result.setForecastCount(forecasts.size());
            result.setScrapRateCount(scrapRates.size());
            result.setInventoryDaysCount(invDays.size());
            result.setOperatingDaysCount(opDays.size());
            result.setBomCount(boms.size());
            result.setInventoryCountCount(invCounts.size());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Sheet parsers (row 0 = header, skip)
    // -------------------------------------------------------------------------

    private List<Forecast> parseForecasts(Sheet sheet) {
        List<Forecast> list = new ArrayList<>();
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String itemCode = str(row, 0);
            if (itemCode == null) continue;
            Forecast f = new Forecast();
            f.setItemCode(itemCode);
            f.setYearMonth((int) num(row, 1));
            f.setQuantity(num(row, 2));
            f.setDeleteFlag(str(row, 3));
            list.add(f);
        }
        return list;
    }

    private List<ScrapRate> parseScrapRates(Sheet sheet) {
        List<ScrapRate> list = new ArrayList<>();
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String itemCode = str(row, 0);
            if (itemCode == null) continue;
            ScrapRate s = new ScrapRate();
            s.setItemCode(itemCode);
            s.setScrapRate(num(row, 1));
            list.add(s);
        }
        return list;
    }

    private List<InventoryDays> parseInventoryDays(Sheet sheet) {
        List<InventoryDays> list = new ArrayList<>();
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String itemCode = str(row, 0);
            if (itemCode == null) continue;
            InventoryDays d = new InventoryDays();
            d.setItemCode(itemCode);
            d.setSafetyDays(num(row, 1));
            d.setMaxDays(num(row, 2));
            list.add(d);
        }
        return list;
    }

    private List<OperatingDays> parseOperatingDays(Sheet sheet) {
        List<OperatingDays> list = new ArrayList<>();
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Cell c = row.getCell(0);
            if (c == null || c.getCellType() == CellType.BLANK) continue;
            OperatingDays o = new OperatingDays();
            o.setYearMonth((int) num(row, 0));
            o.setDays(num(row, 1));
            list.add(o);
        }
        return list;
    }

    private List<Bom> parseBoms(Sheet sheet) {
        List<Bom> list = new ArrayList<>();
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String parentCode = str(row, 0);
            if (parentCode == null) continue;
            Bom b = new Bom();
            b.setParentCode(parentCode);
            b.setChildCode(str(row, 1));
            b.setUsageQty(numOrNull(row, 2));
            b.setProcess(str(row, 3));
            b.setEquipment(str(row, 4));
            Double moldRaw = numOrNull(row, 5);
            b.setMoldCavity(moldRaw != null ? moldRaw.intValue() : null);
            b.setCycleTime(numOrNull(row, 6));
            b.setStaffCount(numOrNull(row, 7));
            b.setTaktTime(numOrNull(row, 8));
            list.add(b);
        }
        return list;
    }

    private List<InventoryCount> parseInventoryCounts(Sheet sheet) {
        List<InventoryCount> list = new ArrayList<>();
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String itemCode = str(row, 0);
            if (itemCode == null) continue;
            InventoryCount ic = new InventoryCount();
            ic.setItemCode(itemCode);
            ic.setYearMonth((int) num(row, 1));
            ic.setAvailableQty(num(row, 2));
            ic.setDeleteFlag(str(row, 3));
            list.add(ic);
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Cell helpers
    // -------------------------------------------------------------------------

    private String str(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.STRING) {
            String v = cell.getStringCellValue().trim();
            return v.isEmpty() ? null : v;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            long l = (long) d;
            return (d == l) ? String.valueOf(l) : String.valueOf(d);
        }
        return null;
    }

    private double num(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try { return Double.parseDouble(cell.getStringCellValue().trim()); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    private Double numOrNull(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try { return Double.parseDouble(cell.getStringCellValue().trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
