package com.aps.service;

import lombok.Data;

/** Excel 全量导入结果统计 */
@Data
public class ImportResult {
    private int demandCount;
    private int bomCount;
    private int safetyStockCount;
    private int operatingDaysCount;
    private int inventoryCountCount;
}
