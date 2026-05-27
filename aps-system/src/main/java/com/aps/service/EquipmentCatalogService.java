package com.aps.service;

import com.aps.entity.EquipmentCatalog;
import com.aps.repository.EquipmentCatalogRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@Service
@Transactional
public class EquipmentCatalogService {

    private static final String SHEET_NAME = "设备清单";
    private static final String TEMPLATE_NOTICE = "说明：按制造部门全删全导；制造部门+设备小类唯一；台数必须大于0";

    @Autowired
    private EquipmentCatalogRepository repository;

    public List<EquipmentCatalog> findAll() {
        return repository.findAll();
    }

    public EquipmentCatalog findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EquipmentCatalog not found: " + id));
    }

    public EquipmentCatalog save(EquipmentCatalog entity) {
        validate(entity);
        if (repository.existsByManufacturingDepartmentAndEquipmentModel(
                entity.getManufacturingDepartment(), entity.getEquipmentModel())) {
            throw new IllegalArgumentException("制造部门+设备小类已存在");
        }
        return repository.save(entity);
    }

    public EquipmentCatalog update(Long id, EquipmentCatalog entity) {
        validate(entity);
        EquipmentCatalog existing = findById(id);
        boolean keyChanged = !existing.getManufacturingDepartment().equals(entity.getManufacturingDepartment())
                || !existing.getEquipmentModel().equals(entity.getEquipmentModel());
        if (keyChanged && repository.existsByManufacturingDepartmentAndEquipmentModel(
                entity.getManufacturingDepartment(), entity.getEquipmentModel())) {
            throw new IllegalArgumentException("制造部门+设备小类已存在");
        }
        existing.setManufacturingDepartment(entity.getManufacturingDepartment());
        existing.setEquipmentCategory(entity.getEquipmentCategory());
        existing.setEquipmentBrand(entity.getEquipmentBrand());
        existing.setEquipmentModel(entity.getEquipmentModel());
        existing.setEquipmentCount(entity.getEquipmentCount());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public ImportResult importWorkbook(InputStream inputStream) throws Exception {
        List<EquipmentCatalog> rows = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("缺少 Sheet: " + SHEET_NAME);
            }
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                EquipmentCatalog item = new EquipmentCatalog(
                        null,
                        stringCell(row.getCell(0)),
                        stringCell(row.getCell(1)),
                        stringCell(row.getCell(2)),
                        stringCell(row.getCell(3)),
                        integerCell(row.getCell(4))
                );
                validate(item);

                String key = item.getManufacturingDepartment() + "|" + item.getEquipmentModel();
                if (!uniqueKeys.add(key)) {
                    throw new IllegalArgumentException("导入文件存在重复键: " + key);
                }
                rows.add(item);
            }
        }

        replaceByDepartments(rows);

        ImportResult result = new ImportResult();
        result.setSkippedCount(rows.size());
        return result;
    }

    public byte[] exportWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Row noticeRow = sheet.createRow(0);
            noticeRow.createCell(0).setCellValue(TEMPLATE_NOTICE);

            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("制造部门");
            header.createCell(1).setCellValue("设备大类");
            header.createCell(2).setCellValue("设备品牌");
            header.createCell(3).setCellValue("设备小类");
            header.createCell(4).setCellValue("台数");

            List<EquipmentCatalog> rows = repository.findAll();
            for (int i = 0; i < rows.size(); i++) {
                EquipmentCatalog item = rows.get(i);
                Row row = sheet.createRow(i + 2);
                row.createCell(0).setCellValue(item.getManufacturingDepartment());
                row.createCell(1).setCellValue(item.getEquipmentCategory());
                row.createCell(2).setCellValue(item.getEquipmentBrand());
                row.createCell(3).setCellValue(item.getEquipmentModel());
                row.createCell(4).setCellValue(item.getEquipmentCount());
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    void replaceByDepartments(List<EquipmentCatalog> list) {
        Set<String> departments = new LinkedHashSet<>();
        for (EquipmentCatalog item : list) {
            departments.add(item.getManufacturingDepartment());
        }
        repository.deleteByManufacturingDepartmentIn(departments);
        repository.saveAll(list);
    }

    private void validate(EquipmentCatalog entity) {
        if (isBlank(entity.getManufacturingDepartment())) {
            throw new IllegalArgumentException("制造部门不能为空");
        }
        if (isBlank(entity.getEquipmentCategory())) {
            throw new IllegalArgumentException("设备大类不能为空");
        }
        if (isBlank(entity.getEquipmentBrand())) {
            throw new IllegalArgumentException("设备品牌不能为空");
        }
        if (isBlank(entity.getEquipmentModel())) {
            throw new IllegalArgumentException("设备小类不能为空");
        }
        if (entity.getEquipmentCount() == null || entity.getEquipmentCount() <= 0) {
            throw new IllegalArgumentException("台数必须大于0");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i <= 4; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !stringCell(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String stringCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private Integer integerCell(Cell cell) {
        String value = stringCell(cell);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("台数必须为整数");
        }
    }
}
