package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.InventoryCount;
import com.aps.service.InventoryCountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory-count")
@CrossOrigin
public class InventoryCountController {

    @Autowired
    private InventoryCountService service;

    @GetMapping
    public ApiResponse<List<InventoryCount>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryCount> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ApiResponse<InventoryCount> create(@RequestBody InventoryCount entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<InventoryCount> update(@PathVariable Long id, @RequestBody InventoryCount entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch")
    public ApiResponse<List<InventoryCount>> batchSave(@RequestBody List<InventoryCount> list) {
        return ApiResponse.success(service.saveAll(list));
    }
}
