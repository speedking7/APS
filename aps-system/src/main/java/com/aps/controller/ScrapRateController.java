package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.ScrapRate;
import com.aps.service.ScrapRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scrap-rate")
@CrossOrigin
public class ScrapRateController {

    @Autowired
    private ScrapRateService service;

    @GetMapping
    public ApiResponse<List<ScrapRate>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ScrapRate> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ApiResponse<ScrapRate> create(@RequestBody ScrapRate entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScrapRate> update(@PathVariable Long id, @RequestBody ScrapRate entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch")
    public ApiResponse<List<ScrapRate>> batchSave(@RequestBody List<ScrapRate> list) {
        return ApiResponse.success(service.saveAll(list));
    }
}
