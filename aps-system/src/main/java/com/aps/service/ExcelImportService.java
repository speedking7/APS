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
 * Excel 导入服务
 *
 * 每次调用处理一个文件，通过 Sheet 名称自动识别数据类型：
 *   完成品入库需求数  → t_demand         （按版本号+客户全删全导）
 *   BOM              → t_bom             （按版本号全删全导）
 *   半成品期末盘点数  → t_inventory_count（按版本号全删全导）
 *   半成品安全库存    → t_safety_stock    （按版本号全删全导）
 *   稼动天数          → t_operating_days  （按年月 upsert）
 */
@Service
@Transactional
public class ExcelImportService {

    @Autowired private DemandRepository         demandRepository;
    @Autowired private BomRepository            bomRepository;
    @Autowired private InventoryCountRepository inventoryCountRepository;
    @Autowired private SafetyStockRepository    safetyStockRepository;
    @Autowired private OperatingDaysRepository  operatingDaysRepository;

    public ImportResult importFromExcel(InputStream inputStream) throws IOException {
        ImportResult result = new ImportResult();
        try (Workbook wb = new XSSFWorkbook(inputStream)) {
            // 按 sheet 名称路由
            Sheet demandSheet    = wb.getSheet("完成品入库需求数");
            Sheet bomSheet       = wb.getSheet("BOM");
            Sheet inventorySheet = wb.getSheet("半成品期末盘点数");
            Sheet safetySheet    = wb.getSheet("半成品安全库存");
            Sheet opDaysSheet    = wb.getSheet("稼动天数");

            if (demandSheet != null) {
                List<Demand> list = parseDemands(demandSheet);
                if (!list.isEmpty()) {
                    demandRepository.deleteAllInBatch();
                    demandRepository.saveAll(list);
                }
                result.setDemandCount(list.size());
            }

            if (bomSheet != null) {
                List<Bom> list = parseBoms(bomSheet);
                if (!list.isEmpty()) {
                    bomRepository.deleteAllInBatch();
                    bomRepository.saveAll(list);
                }
                result.setBomCount(list.size());
            }

            if (inventorySheet != null) {
                List<InventoryCount> list = parseInventoryCounts(inventorySheet);
                if (!list.isEmpty()) {
                    inventoryCountRepository.deleteAllInBatch();
                    inventoryCountRepository.saveAll(list);
                }
                result.setInventoryCountCount(list.size());
            }

            if (safetySheet != null) {
                List<SafetyStock> list = parseSafetyStocks(safetySheet);
                if (!list.isEmpty()) {
                    safetyStockRepository.deleteAllInBatch();
                    safetyStockRepository.saveAll(list);
                }
                result.setSafetyStockCount(list.size());
            }

            if (opDaysSheet != null) {
                List<OperatingDays> list = parseOperatingDays(opDaysSheet);
                for (OperatingDays od : list) {
                    operatingDaysRepository.findByYearMonth(od.getYearMonth())
                        .ifPresentOrElse(existing -> {
                            existing.setTotalDays(od.getTotalDays());
                            existing.setWorkDays(od.getWorkDays());
                            existing.setWeekendDays(od.getWeekendDays());
                            existing.setHolidayDays(od.getHolidayDays());
                            operatingDaysRepository.save(existing);
                        }, () -> operatingDaysRepository.save(od));
                }
                result.setOperatingDaysCount(list.size());
            }
        }
        return result;
    }

    // ── 解析器 ─────────────────────────────────────────────────────────────────

    private List<Demand> parseDemands(Sheet sheet) {
        List<Demand> list = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String itemCode = str(row, 1);
            if (itemCode == null) continue;
            Demand d = new Demand();
            d.setCustomer(str(row, 0));
            d.setItemCode(itemCode);
            d.setYearMonth((int) num(row, 2));
            d.setDemandQty(numOrNull(row, 3));
            d.setEndingInventory(numOrNull(row, 4));
            d.setMinSafetyStock(numOrNull(row, 5));
            d.setNetDemand(numOrNull(row, 6));
            d.setVersion(str(row, 7));
            list.add(d);
        }
        return list;
    }

    private List<Bom> parseBoms(Sheet sheet) {
        List<Bom> list = new ArrayList<>();
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
            b.setScrapRate(numOrNull(row, 9));
            b.setVersion(str(row, 10));
            list.add(b);
        }
        return list;
    }

    private List<InventoryCount> parseInventoryCounts(Sheet sheet) {
        List<InventoryCount> list = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String itemCode = str(row, 0);
            if (itemCode == null) continue;
            InventoryCount ic = new InventoryCount();
            ic.setItemCode(itemCode);
            ic.setYearMonth((int) num(row, 1));
            ic.setAvailableQty(num(row, 2));
            ic.setVersion(str(row, 3));
            list.add(ic);
        }
        return list;
    }

    private List<SafetyStock> parseSafetyStocks(Sheet sheet) {
        List<SafetyStock> list = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String itemCode = str(row, 0);
            if (itemCode == null) continue;
            SafetyStock s = new SafetyStock();
            s.setItemCode(itemCode);
            s.setDailyEquivalent(numOrNull(row, 1));
            s.setSafetyDays(num(row, 2));
            s.setMaxDays(numOrNull(row, 3));
            s.setVersion(str(row, 4));
            list.add(s);
        }
        return list;
    }

    private List<OperatingDays> parseOperatingDays(Sheet sheet) {
        List<OperatingDays> list = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Cell c = row.getCell(0);
            if (c == null || c.getCellType() == CellType.BLANK) continue;
            OperatingDays o = new OperatingDays();
            o.setYearMonth((int) num(row, 0));
            o.setTotalDays(numOrNull(row, 1));
            o.setWorkDays(num(row, 2));
            o.setWeekendDays(numOrNull(row, 3));
            o.setHolidayDays(numOrNull(row, 4));
            list.add(o);
        }
        return list;
    }

    // ── Cell helpers ──────────────────────────────────────────────────────────

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
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) return cell.getNumericCellValue();
        if (type == CellType.STRING) {
            try { return Double.parseDouble(cell.getStringCellValue().trim()); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    private Double numOrNull(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) return cell.getNumericCellValue();
        if (type == CellType.STRING) {
            try { return Double.parseDouble(cell.getStringCellValue().trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
