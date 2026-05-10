package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.OperatingDays;
import com.aps.service.OperatingDaysService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operating-days")
@CrossOrigin
public class OperatingDaysController {

    @Autowired
    private OperatingDaysService service;

    @GetMapping
    public ApiResponse<List<OperatingDays>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<OperatingDays> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ApiResponse<OperatingDays> create(@RequestBody OperatingDays entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<OperatingDays> update(@PathVariable Long id, @RequestBody OperatingDays entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch")
    public ApiResponse<List<OperatingDays>> batchSave(@RequestBody List<OperatingDays> list) {
        return ApiResponse.success(service.saveAll(list));
    }
}
