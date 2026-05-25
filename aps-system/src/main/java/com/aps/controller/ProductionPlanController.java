package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.dto.CalculateRequest;
import com.aps.dto.ProductionPlanView;
import com.aps.service.PlanCalculationService;
import com.aps.service.ProductionPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production-plan")
@CrossOrigin
public class ProductionPlanController {

    @Autowired
    private ProductionPlanService service;

    @Autowired
    private PlanCalculationService planCalculationService;

    /** 触发计算（必须指定单一基础数据版本） */
    @PostMapping("/calculate")
    public ApiResponse<String> calculate(@RequestBody CalculateRequest req) {
        planCalculationService.calculate(req);
        return ApiResponse.success("calculation completed");
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
}
