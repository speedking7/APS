package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.dto.CalculateRequest;
import com.aps.dto.CalculationTaskResponse;
import com.aps.dto.ProductionPlanView;
import com.aps.service.CalculationTaskService;
import com.aps.service.ProductionPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production-plan")
@CrossOrigin
public class ProductionPlanController {

    @Autowired
    private ProductionPlanService service;

    @Autowired
    private CalculationTaskService calculationTaskService;

    /** 触发计算（必须指定单一基础数据版本） */
    @PostMapping("/calculate")
    public ApiResponse<CalculationTaskResponse> calculate(@RequestBody CalculateRequest req) {
        return ApiResponse.success(calculationTaskService.submit(req));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<CalculationTaskResponse> getTask(@PathVariable String taskId) {
        return ApiResponse.success(calculationTaskService.getTask(taskId));
    }

    @GetMapping("/versions")
    public ApiResponse<List<String>> getVersions() {
        return ApiResponse.success(service.findDistinctVersions());
    }

    @GetMapping
    public ApiResponse<List<ProductionPlanView>> findAll() {
        return ApiResponse.success(service.findAllViews());
    }

    @GetMapping("/by-version/{version}")
    public ApiResponse<List<ProductionPlanView>> findByVersion(@PathVariable String version) {
        return ApiResponse.success(service.findViewsByVersion(version));
    }

    @GetMapping("/by-period/{yearMonth}")
    public ApiResponse<List<ProductionPlanView>> findByPeriod(@PathVariable Integer yearMonth) {
        return ApiResponse.success(service.findViewsByYearMonth(yearMonth));
    }

    @GetMapping("/by-product/{code}")
    public ApiResponse<List<ProductionPlanView>> findByProduct(@PathVariable String code) {
        return ApiResponse.success(service.findViewsByFinishedProductCode(code));
    }

    @GetMapping("/by-product/{code}/period/{yearMonth}")
    public ApiResponse<List<ProductionPlanView>> findByProductAndPeriod(
            @PathVariable String code,
            @PathVariable Integer yearMonth) {
        return ApiResponse.success(service.findViewsByFinishedProductCodeAndYearMonth(code, yearMonth));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkbook(
            @RequestParam(required = false) String version,
            @RequestParam(required = false) Integer yearMonth,
            @RequestParam(required = false) String finishedProductCode,
            @RequestParam(required = false) String itemCode) throws Exception {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=production-plan.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.exportWorkbook(version, yearMonth, finishedProductCode, itemCode));
    }
}
