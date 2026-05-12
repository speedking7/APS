package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.Demand;
import com.aps.service.DemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/demand")
@CrossOrigin
public class DemandController {

    @Autowired
    private DemandService service;

    @GetMapping
    public ApiResponse<List<Demand>> findAll() { return ApiResponse.success(service.findAll()); }

    @GetMapping("/{id}")
    public ApiResponse<Demand> findById(@PathVariable Long id) { return ApiResponse.success(service.findById(id)); }

    @PostMapping
    public ApiResponse<Demand> create(@RequestBody Demand entity) { return ApiResponse.success(service.save(entity)); }

    @PutMapping("/{id}")
    public ApiResponse<Demand> update(@PathVariable Long id, @RequestBody Demand entity) { return ApiResponse.success(service.update(id, entity)); }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }

    @PostMapping("/batch")
    public ApiResponse<List<Demand>> batchSave(@RequestBody List<Demand> list) { return ApiResponse.success(service.saveAll(list)); }
}
