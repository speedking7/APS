package com.aps.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备负荷测算行（报表 DTO）
 *
 * taskTimeHours     = sum(planQty × taktTime / 3600)
 * availableTimeHours = operatingDays × 10.5（每日默认作业时间）
 * utilizationRate   = taskTimeHours / availableTimeHours
 * status: LOOSE(<50%), NORMAL(50-85%), TIGHT(85-100%), OVERLOADED(>=100%)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentLoadRow {
    private String equipment;
    private String process;
    private Integer yearMonth;
    private Double taskTimeHours;
    private Double availableTimeHours;
    private Double utilizationRate;
    private String status;
}
