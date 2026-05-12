package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.SafetyStock;
import com.aps.service.SafetyStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/safety-stock")
@CrossOrigin
public class SafetyStockController {

    @Autowired
    private SafetyStockService service;

    @GetMapping
    public ApiResponse<List<SafetyStock>> findAll() { return ApiResponse.success(service.findAll()); }

    @GetMapping("/{id}")
    public ApiResponse<SafetyStock> findById(@PathVariable Long id) { return ApiResponse.success(service.findById(id)); }

    @PostMapping
    public ApiResponse<SafetyStock> create(@RequestBody SafetyStock entity) { return ApiResponse.success(service.save(entity)); }

    @PutMapping("/{id}")
    public ApiResponse<SafetyStock> update(@PathVariable Long id, @RequestBody SafetyStock entity) { return ApiResponse.success(service.update(id, entity)); }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }

    @PostMapping("/batch")
    public ApiResponse<List<SafetyStock>> batchSave(@RequestBody List<SafetyStock> list) { return ApiResponse.success(service.saveAll(list)); }
}
