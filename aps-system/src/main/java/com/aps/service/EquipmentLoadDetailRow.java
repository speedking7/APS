package com.aps.service;

import lombok.Data;

@Data
public class EquipmentLoadDetailRow {
    private String itemCode;
    private String finishedProductCode;
    private Integer yearMonth;
    private String process;
    private String equipment;
    private Integer moldCavity;
    private Double planQty;
    private Double cycleTime;
    private Double requiredSecondsRaw;
    private Double requiredSecondsEffective;
    private Boolean sharedMoldAdjusted;
    private Boolean sharedMoldSuppressed;
    private String sharedMoldGroupKey;
    private String sharedMoldPeerItemCode;
}
