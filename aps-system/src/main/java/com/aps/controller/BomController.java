package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.Bom;
import com.aps.service.BomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bom")
@CrossOrigin
public class BomController {

    @Autowired
    private BomService service;

    @GetMapping
    public ApiResponse<List<Bom>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Bom> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ApiResponse<Bom> create(@RequestBody Bom entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<Bom> update(@PathVariable Long id, @RequestBody Bom entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch")
    public ApiResponse<List<Bom>> batchSave(@RequestBody List<Bom> list) {
        return ApiResponse.success(service.saveAll(list));
    }
}
