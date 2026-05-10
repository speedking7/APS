package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.service.ExcelImportService;
import com.aps.service.ImportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Excel 模板全量导入接口
 *
 * POST /api/excel/import  multipart/form-data, param: file
 */
@RestController
@RequestMapping("/api/excel")
@CrossOrigin
public class ExcelImportController {

    @Autowired
    private ExcelImportService excelImportService;

    @PostMapping("/import")
    public ApiResponse<ImportResult> importExcel(
            @RequestParam(value = "file", required = false) MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 Excel 文件");
        }
        ImportResult result = excelImportService.importFromExcel(file.getInputStream());
        return ApiResponse.success(result);
    }
}
