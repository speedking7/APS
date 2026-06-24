package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.entity.PartMaster;
import com.aps.service.ImportResult;
import com.aps.service.PartMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/part-master")
@CrossOrigin
public class PartMasterController {

    @Autowired
    private PartMasterService service;

    @GetMapping
    public ApiResponse<List<PartMaster>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<PartMaster> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @GetMapping("/by-part-no/{partNo}")
    public ApiResponse<PartMaster> findByPartNo(@PathVariable String partNo) {
        return ApiResponse.success(service.findByPartNo(partNo));
    }

    @PostMapping
    public ApiResponse<PartMaster> create(@RequestBody PartMaster entity) {
        return ApiResponse.success(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<PartMaster> update(@PathVariable Long id, @RequestBody PartMaster entity) {
        return ApiResponse.success(service.update(id, entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch")
    public ApiResponse<List<PartMaster>> batchUpsert(@RequestBody List<PartMaster> list) {
        return ApiResponse.success(service.saveAllUpsert(list));
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template-part-master.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.exportWorkbook());
    }
}
