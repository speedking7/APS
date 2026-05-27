package com.aps.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备负荷测算行（报表 DTO）
 *
 * 当前同时承载两类字段：
 * 1. 设备分析页面口径字段
 * 2. 旧聚合接口兼容字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentLoadRow {
    private String manufacturingDepartment;
    private String equipmentCategory;
    private String equipmentBrand;
    private String equipmentModel;
    private Integer equipmentCount;
    private Double workDays;
    private Double dailyHours;
    private Double planQty;
    private Double cycleTime;
    private Integer moldCavity;
    private Double requiredSeconds;
    private Double availableSecondsPerMachine;
    private Double availableSecondsTotal;
    private Double requiredMachineCount;
    private Double difference;
    private Double loadRate;
    private Boolean matchedCatalog;
    private Boolean sharedMoldAdjusted;
    private Boolean sharedMoldSuppressed;
    private String sharedMoldGroupKey;
    private List<EquipmentLoadDetailRow> detailRows = new ArrayList<>();

    private String equipment;
    private String process;
    private Integer yearMonth;
    private Double taskTimeHours;
    private Double availableTimeHours;
    private Double utilizationRate;
    private String status;
}
