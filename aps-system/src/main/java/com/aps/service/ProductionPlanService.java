package com.aps.service;

import com.aps.dto.ProductionPlanView;
import com.aps.entity.Demand;
import com.aps.entity.PartMaster;
import com.aps.entity.ProductionPlan;
import com.aps.repository.DemandRepository;
import com.aps.repository.PartMasterRepository;
import com.aps.repository.ProductionPlanRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class ProductionPlanService {

    @Autowired
    private ProductionPlanRepository repository;

    @Autowired
    private PartMasterRepository partMasterRepository;

    @Autowired
    private DemandRepository demandRepository;

    public List<ProductionPlan> findAll() {
        return repository.findAll();
    }

    public List<ProductionPlan> findByYearMonth(Integer yearMonth) {
        return repository.findByYearMonth(yearMonth);
    }

    public List<ProductionPlan> findByFinishedProductCode(String code) {
        return repository.findByFinishedProductCode(code);
    }

    public List<ProductionPlan> findByFinishedProductCodeAndYearMonth(String code, Integer yearMonth) {
        return repository.findByFinishedProductCodeAndYearMonth(code, yearMonth);
    }

    public List<String> findDistinctVersions() {
        return repository.findAll().stream()
                .map(ProductionPlan::getVersion)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<ProductionPlan> findByVersion(String version) {
        return repository.findByVersion(version);
    }

    public List<ProductionPlanView> findAllViews() {
        return toViews(repository.findAll());
    }

    public List<ProductionPlanView> findViewsByVersion(String version) {
        return toViews(repository.findByVersion(version));
    }

    public List<ProductionPlanView> findViewsByYearMonth(Integer yearMonth) {
        return toViews(repository.findByYearMonth(yearMonth));
    }

    public List<ProductionPlanView> findViewsByFinishedProductCode(String code) {
        return toViews(repository.findByFinishedProductCode(code));
    }

    public List<ProductionPlanView> findViewsByFinishedProductCodeAndYearMonth(String code, Integer yearMonth) {
        return toViews(repository.findByFinishedProductCodeAndYearMonth(code, yearMonth));
    }

    public byte[] exportWorkbook(String version, Integer yearMonth, String finishedProductCode, String itemCode, String partAttribute) throws Exception {
        List<ProductionPlanView> views = filterViews(version, yearMonth, finishedProductCode, itemCode, partAttribute);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("计划结果");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "完成品", "自制件编码", "自制件名称", "自制件番号", "自制件项目", "子零件属性", "年月",
                    "工序", "设备", "制造部门", "制造单元", "需求(毛)", "计划数量",
                    "报废率", "安全天数", "当前库存", "版本", "是否生产"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            for (int i = 0; i < views.size(); i++) {
                ProductionPlanView view = views.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(defaultString(view.getFinishedProductCode()));
                row.createCell(1).setCellValue(defaultString(view.getItemCode()));
                row.createCell(2).setCellValue(defaultString(view.getItemProductName()));
                row.createCell(3).setCellValue(defaultString(view.getItemProductNo()));
                row.createCell(4).setCellValue(defaultString(view.getItemProjectName()));
                row.createCell(5).setCellValue(defaultString(view.getPartAttribute()));
                writeInteger(row, 6, view.getYearMonth());
                row.createCell(7).setCellValue(defaultString(view.getProcess()));
                row.createCell(8).setCellValue(defaultString(view.getEquipment()));
                row.createCell(9).setCellValue(defaultString(view.getManufacturingDepartment()));
                row.createCell(10).setCellValue(defaultString(view.getManufacturingUnit()));
                writeDouble(row, 11, view.getForecast());
                writeDouble(row, 12, view.getPlanQty());
                writeDouble(row, 13, view.getScrapRate());
                writeDouble(row, 14, view.getSafetyDays());
                writeDouble(row, 15, view.getCurrentInventory());
                row.createCell(16).setCellValue(defaultString(view.getVersion()));
                row.createCell(17).setCellValue(defaultString(view.getIsProduce()));
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public List<ProductionPlanView> filterViews(String version, Integer yearMonth, String finishedProductCode, String itemCode, String partAttribute) {
        return findAllViews().stream()
                .filter(view -> isBlank(version) || version.equals(view.getVersion()))
                .filter(view -> yearMonth == null || yearMonth.equals(view.getYearMonth()))
                .filter(view -> isBlank(finishedProductCode) || finishedProductCode.equals(view.getFinishedProductCode()))
                .filter(view -> isBlank(itemCode) || itemCode.equals(view.getItemCode()))
                .filter(view -> isBlank(partAttribute) || partAttribute.equals(view.getPartAttribute()))
                .collect(Collectors.toList());
    }

    private List<ProductionPlanView> toViews(List<ProductionPlan> plans) {
        Set<String> partNos = plans.stream()
                .flatMap(p -> Stream.of(p.getItemCode(), p.getFinishedProductCode()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, PartMaster> masterMap = loadPartMasterMap(partNos);
        Map<String, Demand> demandMap = loadDemandMap(plans);
        Map<String, Double> previousEndingInventoryMap = buildPreviousEndingInventoryMap(plans);

        return plans.stream().map(plan -> {
            ProductionPlanView view = new ProductionPlanView();
            BeanUtils.copyProperties(plan, view);

            PartMaster item = masterMap.get(plan.getItemCode());
            if (item != null) {
                view.setItemProductName(item.getProductName());
                view.setItemProductNo(item.getProductNo());
                view.setItemProjectName(item.getProjectName());
            }

            PartMaster finished = masterMap.get(plan.getFinishedProductCode());
            if (finished != null) {
                view.setFinishedProductName(finished.getProductName());
                view.setFinishedProductNo(finished.getProductNo());
                view.setFinishedProjectName(finished.getProjectName());
            }

            Demand demand = demandMap.get(buildDemandKey(plan.getFinishedProductCode(), plan.getYearMonth(), plan.getVersion()));
            if (demand != null) {
                view.setDemandQty(demand.getDemandQty());
                view.setEndingInventory(demand.getEndingInventory());
                view.setMinSafetyStock(demand.getMinSafetyStock());
            }

            if (Objects.equals(plan.getFinishedProductCode(), plan.getItemCode())) {
                view.setPreviousPeriodEndingInventory(previousEndingInventoryMap.get(buildDemandKey(
                        plan.getFinishedProductCode(), plan.getYearMonth(), plan.getVersion())));
            }

            return view;
        }).collect(Collectors.toList());
    }

    private Map<String, PartMaster> loadPartMasterMap(Collection<String> partNos) {
        if (partNos == null || partNos.isEmpty()) {
            return Map.of();
        }
        return partMasterRepository.findByPartNoIn(partNos).stream()
                .collect(Collectors.toMap(PartMaster::getPartNo, Function.identity()));
    }

    private Map<String, Demand> loadDemandMap(List<ProductionPlan> plans) {
        Set<String> versions = plans.stream()
                .map(ProductionPlan::getVersion)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (versions.isEmpty()) {
            return Map.of();
        }
        return versions.stream()
                .flatMap(version -> demandRepository.findByVersion(version).stream())
                .collect(Collectors.toMap(
                        demand -> buildDemandKey(demand.getItemCode(), demand.getYearMonth(), demand.getVersion()),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private Map<String, Double> buildPreviousEndingInventoryMap(List<ProductionPlan> plans) {
        Map<String, List<Demand>> demandsByItemAndVersion = plans.stream()
                .map(ProductionPlan::getFinishedProductCode)
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(itemCode -> plans.stream()
                        .map(ProductionPlan::getVersion)
                        .filter(Objects::nonNull)
                        .distinct()
                        .flatMap(version -> demandRepository.findByVersion(version).stream()
                                .filter(demand -> itemCode.equals(demand.getItemCode()))))
                .collect(Collectors.groupingBy(demand -> demand.getItemCode() + "#" + demand.getVersion()));

        return demandsByItemAndVersion.values().stream()
                .flatMap(demands -> {
                    List<Demand> sorted = demands.stream()
                            .sorted((left, right) -> left.getYearMonth().compareTo(right.getYearMonth()))
                            .collect(Collectors.toList());
                    java.util.Map<String, Double> perItem = new java.util.HashMap<>();
                    Double carryover = null;
                    for (Demand demand : sorted) {
                        String key = buildDemandKey(demand.getItemCode(), demand.getYearMonth(), demand.getVersion());
                        if (carryover != null) {
                            perItem.put(key, carryover);
                        }
                        double demandQty = demand.getDemandQty() != null ? demand.getDemandQty() : 0.0;
                        double safetyStock = demand.getMinSafetyStock() != null ? demand.getMinSafetyStock() : 0.0;
                        double available = carryover != null
                                ? carryover
                                : (demand.getEndingInventory() != null ? demand.getEndingInventory() : 0.0);
                        double netDemand = Math.max(0.0, demandQty + safetyStock - available);
                        carryover = Math.max(0.0, available - demandQty) + netDemand;
                    }
                    return perItem.entrySet().stream();
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String buildDemandKey(String itemCode, Integer yearMonth, String version) {
        return itemCode + "#" + yearMonth + "#" + version;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
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
