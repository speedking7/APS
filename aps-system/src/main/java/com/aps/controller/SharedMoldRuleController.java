package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.SharedMoldRule;
import com.aps.service.ImportResult;
import com.aps.service.SharedMoldRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/shared-mold-rules")
@CrossOrigin
public class SharedMoldRuleController {

    @Autowired
    private SharedMoldRuleService service;

    @GetMapping
    public ApiResponse<List<SharedMoldRule>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @PostMapping
    public ApiResponse<SharedMoldRule> create(@RequestBody SharedMoldRule entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<SharedMoldRule> update(@PathVariable Long id, @RequestBody SharedMoldRule entity) {
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template-shared-mold-rules.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.exportWorkbook());
    }
}
