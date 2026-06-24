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
 *   完成品入库需求数  → t_demand         （全删全导）
 *   BOM              → t_bom             （全删全导）
 *   半成品期末盘点数  → t_inventory_count（全删全导）
 *   半成品安全库存    → t_safety_stock    （全删全导）
 *   稼动天数          → t_operating_days  （按年月 upsert）
 *
 * 每行数据在写入前均做基本校验，不合法行记录到 ImportResult.errors 并跳过。
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

            Sheet demandSheet    = wb.getSheet("完成品入库需求数");
            Sheet bomSheet       = wb.getSheet("BOM");
            Sheet inventorySheet = wb.getSheet("半成品期末盘点数");
            Sheet safetySheet    = wb.getSheet("半成品安全库存");
            Sheet opDaysSheet    = wb.getSheet("稼动天数");

            if (demandSheet != null) {
                List<Demand> list = parseDemands(demandSheet, result);
                if (!list.isEmpty()) {
                    // 按 客户+版本号 全删全导，不影响其他客户或版本的数据
                    list.stream()
                        .map(d -> d.getCustomer() + "\0" + d.getVersion())
                        .distinct()
                        .forEach(key -> {
                            String[] parts = key.split("\0", 2);
                            demandRepository.deleteByVersionAndCustomer(parts[1], parts[0]);
                        });
                    demandRepository.flush();
                    demandRepository.saveAll(list);
                }
                result.setDemandCount(list.size());
            }

            if (bomSheet != null) {
                List<Bom> list = parseBoms(bomSheet, result);
                if (!list.isEmpty()) {
                    // 按版本号全删全导，不影响其他版本的数据
                    list.stream().map(Bom::getVersion).distinct()
                        .forEach(bomRepository::deleteByVersion);
                    bomRepository.flush();
                    bomRepository.saveAll(list);
                }
                result.setBomCount(list.size());
            }

            if (inventorySheet != null) {
                List<InventoryCount> list = parseInventoryCounts(inventorySheet, result);
                if (!list.isEmpty()) {
                    list.stream().map(InventoryCount::getVersion).distinct()
                        .forEach(inventoryCountRepository::deleteByVersion);
                    inventoryCountRepository.flush();
                    inventoryCountRepository.saveAll(list);
                }
                result.setInventoryCountCount(list.size());
            }

            if (safetySheet != null) {
                List<SafetyStock> list = parseSafetyStocks(safetySheet, result);
                if (!list.isEmpty()) {
                    list.stream().map(SafetyStock::getVersion).distinct()
                        .forEach(safetyStockRepository::deleteByVersion);
                    safetyStockRepository.flush();
                    safetyStockRepository.saveAll(list);
                }
                result.setSafetyStockCount(list.size());
            }

            if (opDaysSheet != null) {
                List<OperatingDays> list = parseOperatingDays(opDaysSheet, result);
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

    private List<Demand> parseDemands(Sheet sheet, ImportResult result) {
        final String SN = "完成品入库需求数";
        List<Demand> list = new ArrayList<>();
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) continue;

            String customer  = str(row, 0);
            String itemCode  = str(row, 1);
            Double ymRaw     = numOrNull(row, 2);
            String version   = str(row, 7);

            String err = null;
            if (!isValidCode(itemCode))       err = "存货编码无效: " + itemCode;
            else if (ymRaw == null)            err = "年月不能为空";
            else if (!isValidYearMonth(ymRaw)) err = "年月格式无效(需YYYYMM): " + ymRaw.intValue();
            else if (!isValidVersion(version)) err = "版本号不能为空";

            if (err != null) { result.addError(SN, i + 1, err); continue; }

            Demand d = new Demand();
            d.setCustomer(customer);
            d.setItemCode(itemCode);
            d.setYearMonth(ymRaw.intValue());
            d.setDemandQty(numOrNull(row, 3));
            d.setEndingInventory(numOrNull(row, 4));
            d.setMinSafetyStock(numOrNull(row, 5));
            d.setNetDemand(numOrNull(row, 6));
            d.setVersion(version);
            list.add(d);
        }
        return list;
    }

    private List<Bom> parseBoms(Sheet sheet, ImportResult result) {
        final String SN = "BOM";
        List<Bom> list = new ArrayList<>();
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) continue;

            String rootProductCode = str(row, 0);
            String parentCode = str(row, 1);
            String childCode  = str(row, 2);   // 叶节点工艺行允许为空
            Double usageQty   = numOrNull(row, 3);
            String manufacturingDepartment = str(row, 6);
            String manufacturingUnit = str(row, 7);
            Double scrapRate  = numOrNull(row, 12);
            String version    = str(row, 13);

            String err = null;
            if (!isValidCode(rootProductCode)) err = "根完成品编码无效: " + rootProductCode;
            else if (!isValidCode(parentCode))      err = "父零件编码无效: " + parentCode;
            // 有子零件时才校验子零件编码和用量；无子零件表示叶节点工艺行
            else if (childCode != null && !isValidCode(childCode))
                                                err = "子零件编码无效: " + childCode;
            else if (childCode != null && (usageQty == null || usageQty <= 0))
                                                err = "有子零件时用量必须大于0: " + usageQty;
            else if (manufacturingDepartment == null || manufacturingDepartment.isBlank())
                                                err = "制造部门必填";
            else if (manufacturingUnit == null || manufacturingUnit.isBlank())
                                                err = "制造单元必填";
            else if (scrapRate != null && (scrapRate < 0 || scrapRate >= 1))
                                                err = "报废率必须在[0,1)范围内: " + scrapRate;
            else if (!isValidVersion(version))  err = "版本号不能为空";

            if (err != null) { result.addError(SN, i + 1, err); continue; }

            Bom b = new Bom();
            b.setRootProductCode(rootProductCode);
            b.setParentCode(parentCode);
            b.setChildCode(childCode);
            b.setUsageQty(usageQty);
            b.setProcess(str(row, 4));
            b.setEquipment(str(row, 5));
            b.setManufacturingDepartment(manufacturingDepartment);
            b.setManufacturingUnit(manufacturingUnit);
            Double moldRaw = numOrNull(row, 8);
            b.setMoldCavity(moldRaw != null ? moldRaw.intValue() : null);
            b.setCycleTime(numOrNull(row, 9));
            b.setStaffCount(numOrNull(row, 10));
            b.setTaktTime(numOrNull(row, 11));
            b.setScrapRate(scrapRate);
            b.setVersion(version);
            list.add(b);
        }
        return list;
    }

    private List<InventoryCount> parseInventoryCounts(Sheet sheet, ImportResult result) {
        final String SN = "半成品期末盘点数";
        List<InventoryCount> list = new ArrayList<>();
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) continue;

            String itemCode = str(row, 0);
            Double ymRaw    = numOrNull(row, 1);
            String version  = str(row, 3);

            String err = null;
            if (!isValidCode(itemCode))        err = "存货编码无效: " + itemCode;
            else if (ymRaw == null)             err = "年月不能为空";
            else if (!isValidYearMonth(ymRaw))  err = "年月格式无效(需YYYYMM): " + ymRaw.intValue();
            else if (!isValidVersion(version))  err = "版本号不能为空";

            if (err != null) { result.addError(SN, i + 1, err); continue; }

            InventoryCount ic = new InventoryCount();
            ic.setItemCode(itemCode);
            ic.setYearMonth(ymRaw.intValue());
            ic.setAvailableQty(num(row, 2));
            ic.setVersion(version);
            list.add(ic);
        }
        return list;
    }

    private List<SafetyStock> parseSafetyStocks(Sheet sheet, ImportResult result) {
        final String SN = "半成品安全库存";
        List<SafetyStock> list = new ArrayList<>();
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) continue;

            String itemCode   = str(row, 0);
            Double ymRaw      = numOrNull(row, 1);
            Double safetyDays = numOrNull(row, 3);
            String version    = str(row, 5);

            String err = null;
            if (!isValidCode(itemCode))        err = "存货编码无效: " + itemCode;
            else if (ymRaw == null)             err = "年月不能为空";
            else if (!isValidYearMonth(ymRaw))  err = "年月格式无效(需YYYYMM): " + ymRaw.intValue();
            else if (safetyDays == null)        err = "安全天数不能为空";
            else if (safetyDays < 0)            err = "安全天数不能为负数: " + safetyDays;
            else if (!isValidVersion(version))  err = "版本号不能为空";

            if (err != null) { result.addError(SN, i + 1, err); continue; }

            SafetyStock s = new SafetyStock();
            s.setItemCode(itemCode);
            s.setYearMonth(ymRaw.intValue());
            s.setDailyEquivalent(numOrNull(row, 2));
            s.setSafetyDays(safetyDays);
            s.setMaxDays(numOrNull(row, 4));
            s.setVersion(version);
            list.add(s);
        }
        return list;
    }

    private List<OperatingDays> parseOperatingDays(Sheet sheet, ImportResult result) {
        final String SN = "稼动天数";
        List<OperatingDays> list = new ArrayList<>();
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) continue;

            Double ymRaw   = numOrNull(row, 0);
            Double workDays = numOrNull(row, 2);

            String err = null;
            if (ymRaw == null)              err = "年月不能为空";
            else if (!isValidYearMonth(ymRaw)) err = "年月格式无效(需YYYYMM): " + ymRaw.intValue();
            else if (workDays == null)       err = "工作日不能为空";
            else if (workDays < 0)           err = "工作日不能为负数: " + workDays;

            if (err != null) { result.addError(SN, i + 1, err); continue; }

            OperatingDays o = new OperatingDays();
            o.setYearMonth(ymRaw.intValue());
            o.setTotalDays(numOrNull(row, 1));
            o.setWorkDays(workDays);
            o.setWeekendDays(numOrNull(row, 3));
            o.setHolidayDays(numOrNull(row, 4));
            list.add(o);
        }
        return list;
    }

    // ── 校验辅助 ──────────────────────────────────────────────────────────────

    /**
     * 合法的 YYYYMM：6位整数，年份 2000-2099，月份 01-12
     */
    private boolean isValidYearMonth(double raw) {
        int ym = (int) raw;
        if (ym != raw) return false;       // 不是整数
        int year  = ym / 100;
        int month = ym % 100;
        return year >= 2000 && year <= 2099 && month >= 1 && month <= 12;
    }

    /**
     * 合法编码：非空、不以中文字符开头（排除说明行/列头）
     */
    private boolean isValidCode(String code) {
        if (code == null || code.isBlank()) return false;
        // 若首字符是中文则认为是说明行
        return !Character.UnicodeScript.of(code.charAt(0)).equals(Character.UnicodeScript.HAN);
    }

    /**
     * 版本号：非空非空白
     */
    private boolean isValidVersion(String version) {
        return version != null && !version.isBlank();
    }

    /**
     * 整行是否全部为空
     */
    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) continue;
                return false;
            }
        }
        return true;
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
