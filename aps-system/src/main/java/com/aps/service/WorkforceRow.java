package com.aps.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一线人员需求测算行（报表 DTO）
 *
 * workforceDemand = sum(planQty × staffCount)，按 (process, equipment, yearMonth) 聚合
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkforceRow {
    private String process;
    private String equipment;
    private Integer yearMonth;
    private Double workforceDemand;
}
