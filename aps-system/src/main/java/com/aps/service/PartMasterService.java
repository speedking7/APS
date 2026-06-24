package com.aps.service;

import com.aps.entity.PartMaster;
import com.aps.repository.PartMasterRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PartMasterService {

    private static final String SHEET_NAME = "零件主数据";
    private static final String TEMPLATE_NOTICE = "说明：partNo 唯一；导入按 partNo 执行新增或更新";

    @Autowired
    private PartMasterRepository repository;

    public List<PartMaster> findAll() {
        return repository.findAll();
    }

    public PartMaster findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PartMaster not found: " + id));
    }

    public PartMaster findByPartNo(String partNo) {
        return repository.findByPartNo(partNo)
                .orElseThrow(() -> new IllegalArgumentException("PartMaster not found: " + partNo));
    }

    public PartMaster save(PartMaster entity) {
        if (repository.existsByPartNo(entity.getPartNo())) {
            throw new IllegalArgumentException("partNo already exists: " + entity.getPartNo());
        }
        return repository.save(entity);
    }

    public PartMaster update(Long id, PartMaster entity) {
        PartMaster existing = findById(id);
        if (!existing.getPartNo().equals(entity.getPartNo()) && repository.existsByPartNo(entity.getPartNo())) {
            throw new IllegalArgumentException("partNo already exists: " + entity.getPartNo());
        }
        existing.setPartNo(entity.getPartNo());
        existing.setProductName(entity.getProductName());
        existing.setProductNo(entity.getProductNo());
        existing.setProjectName(entity.getProjectName());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<PartMaster> saveAllUpsert(List<PartMaster> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }

        List<String> partNos = list.stream()
                .map(PartMaster::getPartNo)
                .collect(Collectors.toList());

        List<PartMaster> existingList = repository.findByPartNoIn(partNos);
        var existingMap = existingList.stream()
                .collect(Collectors.toMap(PartMaster::getPartNo, partMaster -> partMaster));

        List<PartMaster> merged = list.stream().map(item -> {
            PartMaster existing = existingMap.get(item.getPartNo());
            if (existing == null) {
                return item;
            }
            existing.setProductName(item.getProductName());
            existing.setProductNo(item.getProductNo());
            existing.setProjectName(item.getProjectName());
            return existing;
        }).collect(Collectors.toList());

        return repository.saveAll(merged);
    }

    public ImportResult importWorkbook(InputStream inputStream) throws Exception {
        List<PartMaster> rows = new java.util.ArrayList<>();

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
                PartMaster item = new PartMaster(
                        null,
                        stringCell(row, 0),
                        stringCell(row, 1),
                        stringCell(row, 2),
                        stringCell(row, 3)
                );
                validateForImport(item);
                rows.add(item);
            }
        }

        saveAllUpsert(rows);

        ImportResult result = new ImportResult();
        result.setSkippedCount(rows.size());
        return result;
    }

    public byte[] exportWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            CellStyle noticeStyle = workbook.createCellStyle();
            noticeStyle.setAlignment(HorizontalAlignment.LEFT);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            Row noticeRow = sheet.createRow(0);
            noticeRow.createCell(0).setCellValue(TEMPLATE_NOTICE);
            noticeRow.getCell(0).setCellStyle(noticeStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            Row header = sheet.createRow(1);
            String[] headers = {"零件编码", "零件名称", "零件番号", "项目名称"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }

            sheet.setColumnWidth(0, 18 * 256);
            sheet.setColumnWidth(1, 24 * 256);
            sheet.setColumnWidth(2, 18 * 256);
            sheet.setColumnWidth(3, 20 * 256);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void validateForImport(PartMaster entity) {
        if (isBlank(entity.getPartNo())) {
            throw new IllegalArgumentException("零件编码不能为空");
        }
        if (isBlank(entity.getProductName())) {
            throw new IllegalArgumentException("零件名称不能为空");
        }
        if (isBlank(entity.getProductNo())) {
            throw new IllegalArgumentException("零件番号不能为空");
        }
        if (isBlank(entity.getProjectName())) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i <= 3; i++) {
            if (!stringCell(row, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String stringCell(Row row, int index) {
        if (row == null) {
            return "";
        }
        var cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}
