package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.EquipmentCatalog;
import com.aps.service.EquipmentCatalogService;
import com.aps.service.ImportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/equipment-catalog")
@CrossOrigin
public class EquipmentCatalogController {

    @Autowired
    private EquipmentCatalogService service;

    @GetMapping
    public ApiResponse<List<EquipmentCatalog>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<EquipmentCatalog> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping
    public ApiResponse<EquipmentCatalog> create(@RequestBody EquipmentCatalog entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<EquipmentCatalog> update(@PathVariable Long id, @RequestBody EquipmentCatalog entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/import")
    public ApiResponse<ImportResult> importWorkbook(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 Excel 文件");
        }
        return ApiResponse.success(service.importWorkbook(file.getInputStream()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkbook() throws Exception {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template-equipment-catalog.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.exportWorkbook());
    }
}
