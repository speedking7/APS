package com.aps.controller;

import com.aps.common.ApiResponse;
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

    /** 触发计算（全量重算） */
    @PostMapping("/calculate")
    public ApiResponse<String> calculate() {
        planCalculationService.calculate();
        return ApiResponse.success("calculation completed");
    }

    @GetMapping
    public ApiResponse<List<ProductionPlan>> findAll() {
        return ApiResponse.success(service.findAll());
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
