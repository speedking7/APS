package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.service.WorkforceDetailRow;
import com.aps.service.WorkforceDetailService;
import com.aps.service.WorkforceReportService;
import com.aps.service.WorkforceRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 一线人员需求测算报表接口（FUNC-CW-003）
 */
@RestController
@RequestMapping("/api/workforce-report")
@CrossOrigin
public class WorkforceReportController {

    @Autowired
    private WorkforceReportService workforceReportService;

    @Autowired
    private WorkforceDetailService workforceDetailService;

    /**
     * 获取人员需求报表
     *
     * @param periods 期间列表（YYYYMM，可选，不传则查询全部）
     */
    @GetMapping
    public ApiResponse<List<WorkforceRow>> getReport(
            @RequestParam(required = false) List<Integer> periods) {
        return ApiResponse.success(workforceReportService.calculateWorkforceReport(periods));
    }

    /**
     * 获取工时分析明细
     *
     * @param version 计划版本
     */
    @GetMapping("/details")
    public ApiResponse<List<WorkforceDetailRow>> getDetails(
            @RequestParam String version,
            @RequestParam(required = false, defaultValue = "10.5") Double dailyHours) {
        return ApiResponse.success(workforceDetailService.findDetailsByVersion(version, dailyHours));
    }

    @GetMapping("/details/export")
    public ResponseEntity<byte[]> exportDetails(
            @RequestParam String version,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "10.5") Double dailyHours,
            @RequestParam(required = false, defaultValue = "detail") String viewMode) throws Exception {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=workforce-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workforceDetailService.exportWorkbook(version, month, department, unit, process, keyword, viewMode, dailyHours));
    }
}
