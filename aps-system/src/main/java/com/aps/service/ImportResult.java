package com.aps.service;

import lombok.Data;

/** Excel 全量导入结果统计 */
@Data
public class ImportResult {
    private int forecastCount;
    private int scrapRateCount;
    private int inventoryDaysCount;
    private int operatingDaysCount;
    private int bomCount;
    private int inventoryCountCount;
}
