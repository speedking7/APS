package com.aps.service;

import com.aps.dto.ProductionPlanView;
import com.aps.repository.OperatingDaysRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WorkforceDetailService {

    @Autowired
    private ProductionPlanService productionPlanService;

    @Autowired
    private OperatingDaysRepository operatingDaysRepository;

    public List<WorkforceDetailRow> findDetailsByVersion(String version, Double dailyHours) {
        List<ProductionPlanView> plans = productionPlanService.findViewsByVersion(version);
        List<WorkforceDetailRow> rawRows = plans.stream()
                .filter(p -> p.getYearMonth() != null && p.getProcess() != null)
                .map(plan -> toRow(plan, dailyHours))
                .collect(Collectors.toList());

        return rawRows.stream()
                .sorted(Comparator
                        .comparing(WorkforceDetailRow::getYearMonth)
                        .thenComparing(WorkforceDetailRow::getManufacturingDepartment)
                        .thenComparing(WorkforceDetailRow::getManufacturingUnit)
                        .thenComparing(WorkforceDetailRow::getProductCode)
                .thenComparing(WorkforceDetailRow::getProcess))
                .collect(Collectors.toList());
    }

    public byte[] exportWorkbook(
            String version,
            String month,
            String department,
            String unit,
            String process,
            String keyword,
            String viewMode,
            Double dailyHours
    ) throws Exception {
        List<WorkforceDetailRow> filteredRows = filterRows(version, month, department, unit, process, keyword, dailyHours);
        boolean summaryMode = "summary".equalsIgnoreCase(viewMode);
        List<WorkforceDetailRow> exportRows = summaryMode ? summarizeRows(filteredRows) : filteredRows;

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(summaryMode ? "工时分析汇总" : "工时分析");
            Row header = sheet.createRow(0);
            String[] headers = summaryMode
                    ? new String[]{"制造部门", "制造单元", "工序", "月份", "所需工时", "所需人数"}
                    : new String[]{"制造部门", "制造单元", "项目", "产品名称", "产品番号", "产品编码", "月份", "计划生产数", "工序", "所需工时", "所需人数"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            for (int i = 0; i < exportRows.size(); i++) {
                WorkforceDetailRow row = exportRows.get(i);
                Row excelRow = sheet.createRow(i + 1);
                excelRow.createCell(0).setCellValue(defaultText(row.getManufacturingDepartment()));
                excelRow.createCell(1).setCellValue(defaultText(row.getManufacturingUnit()));
                if (summaryMode) {
                    excelRow.createCell(2).setCellValue(defaultText(row.getProcess()));
                    writeInteger(excelRow, 3, row.getYearMonth());
                    writeDouble(excelRow, 4, row.getRequiredHours());
                    writeDouble(excelRow, 5, row.getRequiredPeople());
                } else {
                    excelRow.createCell(2).setCellValue(defaultText(row.getProject()));
                    excelRow.createCell(3).setCellValue(defaultText(row.getProductName()));
                    excelRow.createCell(4).setCellValue(defaultText(row.getProductNo()));
                    excelRow.createCell(5).setCellValue(defaultText(row.getProductCode()));
                    writeInteger(excelRow, 6, row.getYearMonth());
                    writeDouble(excelRow, 7, row.getPlanQty());
                    excelRow.createCell(8).setCellValue(defaultText(row.getProcess()));
                    writeDouble(excelRow, 9, row.getRequiredHours());
                    writeDouble(excelRow, 10, row.getRequiredPeople());
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public List<WorkforceDetailRow> filterRows(
            String version,
            String month,
            String department,
            String unit,
            String process,
            String keyword,
            Double dailyHours
    ) {
        return findDetailsByVersion(version, dailyHours).stream()
                .filter(row -> isBlank(month) || String.valueOf(row.getYearMonth()).equals(month))
                .filter(row -> isBlank(department) || Objects.equals(row.getManufacturingDepartment(), department))
                .filter(row -> isBlank(unit) || Objects.equals(row.getManufacturingUnit(), unit))
                .filter(row -> isBlank(process) || Objects.equals(row.getProcess(), process))
                .filter(row -> {
                    if (isBlank(keyword)) return true;
                    String target = String.join("|",
                            defaultText(row.getProject()),
                            defaultText(row.getProductName()),
                            defaultText(row.getProductNo()),
                            defaultText(row.getProductCode()),
                            defaultText(row.getProcess())
                    ).toLowerCase();
                    return target.contains(keyword.trim().toLowerCase());
                })
                .collect(Collectors.toList());
    }

    public List<WorkforceDetailRow> summarizeRows(List<WorkforceDetailRow> rows) {
        Map<String, WorkforceDetailRow> groups = new LinkedHashMap<>();
        for (WorkforceDetailRow row : rows) {
            String key = String.join("|",
                    defaultText(row.getManufacturingDepartment()),
                    defaultText(row.getManufacturingUnit()),
                    defaultText(row.getProcess()),
                    String.valueOf(row.getYearMonth()));
            WorkforceDetailRow summary = groups.get(key);
            if (summary == null) {
                summary = new WorkforceDetailRow();
                summary.setManufacturingDepartment(row.getManufacturingDepartment());
                summary.setManufacturingUnit(row.getManufacturingUnit());
                summary.setProcess(row.getProcess());
                summary.setYearMonth(row.getYearMonth());
                summary.setWorkDays(row.getWorkDays());
                summary.setDailyHours(row.getDailyHours());
                summary.setRequiredHours(0.0);
                summary.setRequiredPeople(0.0);
                groups.put(key, summary);
            }
            summary.setRequiredHours(summary.getRequiredHours() + toNumber(row.getRequiredHours()));
            summary.setRequiredPeople(summary.getRequiredPeople() + toNumber(row.getRequiredPeople()));
        }
        return groups.values().stream()
                .sorted(Comparator
                        .comparing(WorkforceDetailRow::getYearMonth)
                        .thenComparing(WorkforceDetailRow::getManufacturingDepartment)
                        .thenComparing(WorkforceDetailRow::getManufacturingUnit)
                        .thenComparing(WorkforceDetailRow::getProcess))
                .collect(Collectors.toList());
    }

    private WorkforceDetailRow toRow(ProductionPlanView plan, Double dailyHours) {
        WorkforceDetailRow row = new WorkforceDetailRow();
        row.setManufacturingDepartment(defaultText(plan.getManufacturingDepartment()));
        row.setManufacturingUnit(defaultText(plan.getManufacturingUnit()));
        row.setProject(defaultText(firstNonBlank(plan.getItemProjectName(), plan.getFinishedProjectName())));
        row.setProductName(defaultText(firstNonBlank(plan.getItemProductName(), plan.getFinishedProductName())));
        row.setProductNo(defaultText(firstNonBlank(plan.getItemProductNo(), plan.getFinishedProductNo())));
        row.setProductCode(defaultText(plan.getItemCode()));
        row.setFinishedProductCode(defaultText(plan.getFinishedProductCode()));
        row.setYearMonth(plan.getYearMonth());
        row.setPlanQty(toNumber(plan.getPlanQty()));
        row.setRawPlanQty(plan.getRawPlanQty());
        row.setProcess(defaultText(plan.getProcess()));
        row.setStaffCount(toNumber(plan.getStaffCount()));
        row.setTaktTime(toNumber(plan.getTaktTime()));
        row.setWorkDays(resolveWorkDays(row.getYearMonth()));
        row.setDailyHours(normalizeDailyHours(dailyHours));
        row.setRequiredSeconds(row.getPlanQty() * row.getStaffCount() * row.getTaktTime());
        row.setRequiredHours(row.getRequiredSeconds() / 3600.0);
        row.setRequiredPeople(calculateRequiredPeople(row.getWorkDays(), row.getRequiredHours(), row.getDailyHours()));
        row.setSharedMoldAdjusted(false);
        row.setSharedMoldSuppressed(false);
        return row;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary;
        }
        return fallback;
    }

    private String defaultText(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    private Double toNumber(Double value) {
        return Objects.requireNonNullElse(value, 0.0);
    }

    private Double calculateRequiredPeople(Double workDays, Double requiredHours, Double dailyHours) {
        double hours = toNumber(requiredHours);
        double configuredDailyHours = toNumber(dailyHours);
        double normalizedWorkDays = toNumber(workDays);
        if (hours <= 0 || configuredDailyHours <= 0 || normalizedWorkDays <= 0) {
            return 0.0;
        }
        return hours / normalizedWorkDays / configuredDailyHours;
    }

    private Double resolveWorkDays(Integer yearMonth) {
        if (yearMonth == null) {
            return 0.0;
        }
        return operatingDaysRepository.findByYearMonth(yearMonth)
                .map(od -> toNumber(od.getWorkDays()))
                .orElse(0.0);
    }

    private Double normalizeDailyHours(Double dailyHours) {
        double value = toNumber(dailyHours);
        return value > 0 ? value : 10.5;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void writeDouble(Row row, int columnIndex, Double value) {
        if (value != null) {
            row.createCell(columnIndex).setCellValue(value);
        } else {
            row.createCell(columnIndex).setCellValue("");
        }
    }

    private void writeInteger(Row row, int columnIndex, Integer value) {
        if (value != null) {
            row.createCell(columnIndex).setCellValue(value);
        } else {
            row.createCell(columnIndex).setCellValue("");
        }
    }
}
