package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.dto.CalculateRequest;
import com.aps.entity.ProductionPlan;
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

    /** 触发计算（带版本参数） */
    @PostMapping("/calculate")
    public ApiResponse<String> calculate(@RequestBody(required = false) CalculateRequest req) {
        if (req == null) {
            planCalculationService.calculate();
        } else {
            planCalculationService.calculate(req);
        }
        return ApiResponse.success("calculation completed");
    }

    @GetMapping("/versions")
    public ApiResponse<List<String>> getVersions() {
        return ApiResponse.success(service.findDistinctVersions());
    }

    @GetMapping
    public ApiResponse<List<ProductionPlan>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/by-version/{version}")
    public ApiResponse<List<ProductionPlan>> findByVersion(@PathVariable String version) {
        return ApiResponse.success(service.findByVersion(version));
    }

    @GetMapping("/by-period/{yearMonth}")
    public ApiResponse<List<ProductionPlan>> findByPeriod(@PathVariable Integer yearMonth) {
        return ApiResponse.success(service.findByYearMonth(yearMonth));
    }

    @GetMapping("/by-product/{code}")
    public ApiResponse<List<ProductionPlan>> findByProduct(@PathVariable String code) {
        return ApiResponse.success(service.findByFinishedProductCode(code));
    }

    @GetMapping("/by-product/{code}/period/{yearMonth}")
    public ApiResponse<List<ProductionPlan>> findByProductAndPeriod(
            @PathVariable String code,
            @PathVariable Integer yearMonth) {
        return ApiResponse.success(service.findByFinishedProductCodeAndYearMonth(code, yearMonth));
    }
}
