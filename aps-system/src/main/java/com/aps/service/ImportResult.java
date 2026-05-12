package com.aps.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Excel 全量导入结果统计 */
@Data
public class ImportResult {
    private int demandCount;
    private int bomCount;
    private int safetyStockCount;
    private int operatingDaysCount;
    private int inventoryCountCount;

    /** 所有 sheet 合计跳过的无效行数 */
    private int skippedCount;

    /** 校验错误明细，格式：[SheetName] 第N行: 错误原因 */
    private List<String> errors = new ArrayList<>();

    public void addError(String sheetName, int rowNum, String reason) {
        errors.add(String.format("[%s] 第%d行: %s", sheetName, rowNum, reason));
        skippedCount++;
    }
}
