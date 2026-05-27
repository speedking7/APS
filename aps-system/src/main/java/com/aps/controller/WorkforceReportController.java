package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.service.WorkforceDetailRow;
import com.aps.service.WorkforceDetailService;
import com.aps.service.WorkforceReportService;
import com.aps.service.WorkforceRow;
import org.springframework.beans.factory.annotation.Autowired;
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
            @RequestParam String version) {
        return ApiResponse.success(workforceDetailService.findDetailsByVersion(version));
    }
}
