package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.Forecast;
import com.aps.service.ForecastService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forecast")
@CrossOrigin
public class ForecastController {

    @Autowired
    private ForecastService service;

    @GetMapping
    public ApiResponse<List<Forecast>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Forecast> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ApiResponse<Forecast> create(@RequestBody Forecast entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<Forecast> update(@PathVariable Long id, @RequestBody Forecast entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch")
    public ApiResponse<List<Forecast>> batchSave(@RequestBody List<Forecast> list) {
        return ApiResponse.success(service.saveAll(list));
    }
}
