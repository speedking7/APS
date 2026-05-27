package com.aps.service;

import com.aps.dto.ProductionPlanView;
import com.aps.entity.SharedMoldRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WorkforceDetailService {

    @Autowired
    private ProductionPlanService productionPlanService;

    @Autowired
    private SharedMoldRuleService sharedMoldRuleService;

    public List<WorkforceDetailRow> findDetailsByVersion(String version) {
        List<ProductionPlanView> plans = productionPlanService.findViewsByVersion(version);
        List<WorkforceDetailRow> rawRows = plans.stream()
                .filter(p -> p.getYearMonth() != null && p.getProcess() != null)
                .map(this::toRow)
                .collect(Collectors.toList());

        return applySharedMoldRule(rawRows).stream()
                .sorted(Comparator
                        .comparing(WorkforceDetailRow::getYearMonth)
                        .thenComparing(WorkforceDetailRow::getManufacturingDepartment)
                        .thenComparing(WorkforceDetailRow::getManufacturingUnit)
                        .thenComparing(WorkforceDetailRow::getProductCode)
                        .thenComparing(WorkforceDetailRow::getProcess))
                .collect(Collectors.toList());
    }

    private WorkforceDetailRow toRow(ProductionPlanView plan) {
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
        row.setProcess(defaultText(plan.getProcess()));
        row.setStaffCount(toNumber(plan.getStaffCount()));
        row.setTaktTime(toNumber(plan.getTaktTime()));
        row.setRequiredSeconds(row.getPlanQty() * row.getStaffCount() * row.getTaktTime());
        row.setRequiredHours(row.getRequiredSeconds() / 3600.0);
        return row;
    }

    private List<WorkforceDetailRow> applySharedMoldRule(List<WorkforceDetailRow> rows) {
        Map<String, Set<String>> sharedMoldGroups = buildSharedMoldGroups();
        Map<String, List<WorkforceDetailRow>> grouped = new LinkedHashMap<>();
        List<WorkforceDetailRow> passthrough = new java.util.ArrayList<>();

        for (WorkforceDetailRow row : rows) {
            if (!isSharedMoldSelfProductRow(row, sharedMoldGroups)) {
                passthrough.add(row);
                continue;
            }
            String key = buildSharedMoldKey(row, sharedMoldGroups);
            row.setSharedMoldAdjusted(true);
            row.setSharedMoldGroupKey(key.substring(key.lastIndexOf('|') + 1));
            grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(row);
        }

        for (List<WorkforceDetailRow> groupRows : grouped.values()) {
            WorkforceDetailRow activeRow = groupRows.stream()
                    .max(Comparator.comparingDouble(row -> toNumber(row.getPlanQty())))
                    .orElse(null);
            double maxPlanQty = activeRow == null ? 0.0 : toNumber(activeRow.getPlanQty());
            for (WorkforceDetailRow row : groupRows) {
                if (toNumber(row.getPlanQty()) < maxPlanQty) {
                    row.setSharedMoldSuppressed(true);
                    row.setRequiredSeconds(0.0);
                    row.setRequiredHours(0.0);
                    row.setSharedMoldPeerItemCode(activeRow != null ? activeRow.getProductCode() : null);
                } else {
                    row.setSharedMoldSuppressed(false);
                    row.setSharedMoldPeerItemCode(null);
                }
                passthrough.add(row);
            }
        }

        return passthrough;
    }

    private boolean isSharedMoldSelfProductRow(WorkforceDetailRow row, Map<String, Set<String>> sharedMoldGroups) {
        if (row == null) return false;
        String productCode = row.getProductCode();
        return productCode != null
                && sharedMoldGroups.containsKey(productCode);
    }

    private String buildSharedMoldKey(WorkforceDetailRow row, Map<String, Set<String>> sharedMoldGroups) {
        Set<String> group = sharedMoldGroups.get(row.getProductCode());
        String groupKey = group.stream().sorted().collect(Collectors.joining("|"));
        return row.getYearMonth() + "|" + defaultText(row.getProcess()) + "|" +
                defaultText(row.getManufacturingDepartment()) + "|" +
                defaultText(row.getManufacturingUnit()) + "|" + groupKey;
    }

    private Map<String, Set<String>> buildSharedMoldGroups() {
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        for (SharedMoldRule rule : sharedMoldRuleService.findEnabledRules()) {
            Set<String> pair = Set.of(rule.getProductACode(), rule.getProductBCode());
            groups.put(rule.getProductACode(), pair);
            groups.put(rule.getProductBCode(), pair);
        }
        return groups;
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
}
