package com.aps.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CalculationTaskResponse {
    private String taskId;
    private String status;
    private String version;
    private String resultVersion;
    private String message;
    private Integer progressPercent;
    private String stage;
    private Integer currentPeriod;
    private Integer totalPeriods;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
