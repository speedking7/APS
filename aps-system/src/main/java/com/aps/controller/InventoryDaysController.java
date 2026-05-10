package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.InventoryDays;
import com.aps.service.InventoryDaysService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory-days")
@CrossOrigin
public class InventoryDaysController {

    @Autowired
    private InventoryDaysService service;

    @GetMapping
    public ApiResponse<List<InventoryDays>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryDays> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ApiResponse<InventoryDays> create(@RequestBody InventoryDays entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<InventoryDays> update(@PathVariable Long id, @RequestBody InventoryDays entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch")
    public ApiResponse<List<InventoryDays>> batchSave(@RequestBody List<InventoryDays> list) {
        return ApiResponse.success(service.saveAll(list));
    }
}
