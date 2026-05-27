package com.aps.service;

import com.aps.entity.SharedMoldRule;
import com.aps.repository.SharedMoldRuleRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class SharedMoldRuleService {

    private static final String SHEET_NAME = "共模规则";
    private static final String TEMPLATE_NOTICE = "说明：产品A编码+产品B编码唯一；设备编号/模具编号可为空；是否启用填写 Y 或 N";

    @Autowired
    private SharedMoldRuleRepository repository;

    public List<SharedMoldRule> findAll() {
        return repository.findAll();
    }

    public SharedMoldRule findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SharedMoldRule not found: " + id));
    }

    public SharedMoldRule save(SharedMoldRule entity) {
        normalizePair(entity);
        validate(entity);
        if (repository.existsByProductACodeAndProductBCode(entity.getProductACode(), entity.getProductBCode())) {
            throw new IllegalArgumentException("产品对已存在");
        }
        return repository.save(entity);
    }

    public SharedMoldRule update(Long id, SharedMoldRule entity) {
        normalizePair(entity);
        validate(entity);
        SharedMoldRule existing = findById(id);
        boolean keyChanged = !existing.getProductACode().equals(entity.getProductACode())
                || !existing.getProductBCode().equals(entity.getProductBCode());
        if (keyChanged && repository.existsByProductACodeAndProductBCode(entity.getProductACode(), entity.getProductBCode())) {
            throw new IllegalArgumentException("产品对已存在");
        }
        existing.setProductACode(entity.getProductACode());
        existing.setProductBCode(entity.getProductBCode());
        existing.setEquipmentCode(blankToNull(entity.getEquipmentCode()));
        existing.setMoldCode(blankToNull(entity.getMoldCode()));
        existing.setEnabled(entity.getEnabled());
        existing.setRemark(blankToNull(entity.getRemark()));
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<SharedMoldRule> findEnabledRules() {
        return repository.findByEnabledTrue();
    }

    public ImportResult importWorkbook(InputStream inputStream) throws Exception {
        List<SharedMoldRule> rows = new ArrayList<>();
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
                SharedMoldRule item = new SharedMoldRule(
                        null,
                        stringCell(row, 0),
                        stringCell(row, 1),
                        blankToNull(stringCell(row, 2)),
                        blankToNull(stringCell(row, 3)),
                        parseEnabled(stringCell(row, 4)),
                        blankToNull(stringCell(row, 5))
                );
                normalizePair(item);
                validate(item);
                rows.add(item);
            }
        }

        repository.deleteAll();
        repository.saveAll(rows);

        ImportResult result = new ImportResult();
        result.setSkippedCount(rows.size());
        return result;
    }

    public byte[] exportWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.createRow(0).createCell(0).setCellValue(TEMPLATE_NOTICE);

            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("产品A编码");
            header.createCell(1).setCellValue("产品B编码");
            header.createCell(2).setCellValue("设备编号");
            header.createCell(3).setCellValue("模具编号");
            header.createCell(4).setCellValue("是否启用");
            header.createCell(5).setCellValue("备注");

            List<SharedMoldRule> rows = repository.findAll();
            for (int i = 0; i < rows.size(); i++) {
                SharedMoldRule item = rows.get(i);
                Row row = sheet.createRow(i + 2);
                row.createCell(0).setCellValue(item.getProductACode());
                row.createCell(1).setCellValue(item.getProductBCode());
                row.createCell(2).setCellValue(item.getEquipmentCode() == null ? "" : item.getEquipmentCode());
                row.createCell(3).setCellValue(item.getMoldCode() == null ? "" : item.getMoldCode());
                row.createCell(4).setCellValue(Boolean.TRUE.equals(item.getEnabled()) ? "Y" : "N");
                row.createCell(5).setCellValue(item.getRemark() == null ? "" : item.getRemark());
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void validate(SharedMoldRule entity) {
        if (isBlank(entity.getProductACode())) {
            throw new IllegalArgumentException("产品A编码不能为空");
        }
        if (isBlank(entity.getProductBCode())) {
            throw new IllegalArgumentException("产品B编码不能为空");
        }
        if (entity.getProductACode().equals(entity.getProductBCode())) {
            throw new IllegalArgumentException("产品A编码与产品B编码不能相同");
        }
        if (entity.getEnabled() == null) {
            throw new IllegalArgumentException("是否启用不能为空");
        }
    }

    private void normalizePair(SharedMoldRule entity) {
        if (entity == null || isBlank(entity.getProductACode()) || isBlank(entity.getProductBCode())) {
            return;
        }
        String a = entity.getProductACode().trim();
        String b = entity.getProductBCode().trim();
        if (a.compareTo(b) <= 0) {
            entity.setProductACode(a);
            entity.setProductBCode(b);
        } else {
            entity.setProductACode(b);
            entity.setProductBCode(a);
        }
    }

    private Boolean parseEnabled(String value) {
        if (isBlank(value)) return null;
        if ("Y".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value)) return true;
        if ("N".equalsIgnoreCase(value) || "FALSE".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("是否启用只能填写 Y 或 N");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i <= 5; i++) {
            if (!stringCell(row, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String stringCell(Row row, int index) {
        if (row == null) return "";
        var cell = row.getCell(index);
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
