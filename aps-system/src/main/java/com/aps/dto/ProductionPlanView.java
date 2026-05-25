package com.aps.dto;

import lombok.Data;

@Data
public class ProductionPlanView {

    private Long id;
    private String finishedProductCode;
    private String itemCode;
    private Integer yearMonth;
    private String process;
    private String equipment;
    private String manufacturingDepartment;
    private String manufacturingUnit;
    private Integer moldCavity;
    private Double cycleTime;
    private Double staffCount;
    private Double taktTime;
    private Double currentInventory;
    private Double forecast;
    private Double safetyDays;
    private Double operatingDays;
    private Double scrapRate;
    private String isProduce;
    private Double planQty;
    private String version;

    private String itemProductName;
    private String itemProductNo;
    private String itemProjectName;

    private String finishedProductName;
    private String finishedProductNo;
    private String finishedProjectName;
}
