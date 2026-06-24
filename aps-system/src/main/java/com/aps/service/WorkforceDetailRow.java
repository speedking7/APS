package com.aps.service;

import lombok.Data;

@Data
public class WorkforceDetailRow {
    private String manufacturingDepartment;
    private String manufacturingUnit;
    private String project;
    private String productName;
    private String productNo;
    private String productCode;
    private String finishedProductCode;
    private Integer yearMonth;
    private Double planQty;
    private Double rawPlanQty;
    private String process;
    private Double staffCount;
    private Double taktTime;
    private Double requiredSeconds;
    private Double requiredHours;
    private Boolean sharedMoldAdjusted;
    private Boolean sharedMoldSuppressed;
    private String sharedMoldGroupKey;
    private String sharedMoldPeerItemCode;
}
