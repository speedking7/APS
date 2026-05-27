package com.aps.service;

import com.aps.entity.EquipmentCatalog;
import com.aps.entity.ProductionPlan;
import com.aps.entity.SharedMoldRule;
import com.aps.repository.EquipmentCatalogRepository;
import com.aps.repository.OperatingDaysRepository;
import com.aps.repository.ProductionPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
import java.util.*;

/**
 * 设备负荷测算服务（FUNC-EL-003/004）
 *
 * 计算逻辑：
 *   requiredSeconds      = planQty × cycleTime / moldCavity
 *   requiredMachineCount = Σ(requiredSeconds / availableSeconds)
 *   availableSeconds     = workDays × DEFAULT_HOURS_PER_DAY × 3600
 *   loadRate             = requiredMachineCount / equipmentCount
 */
@Service
@Transactional(readOnly = true)
public class EquipmentLoadService {

    static final double DEFAULT_HOURS_PER_DAY = 10.5;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Autowired
    private OperatingDaysRepository operatingDaysRepository;

    @Autowired
    private EquipmentCatalogRepository equipmentCatalogRepository;

    @Autowired
    private SharedMoldRuleService sharedMoldRuleService;

    /**
     * 计算设备负荷报表
     *
     * @param periods 期间列表（YYYYMM），为 null 时查询全部
     * @return 聚合后的设备负荷行列表，按 yearMonth, equipment 升序
     */
    public List<EquipmentLoadRow> calculateEquipmentLoad(List<Integer> periods) {
        return calculateEquipmentLoad(periods, null);
    }

    public List<EquipmentLoadRow> calculateEquipmentLoad(List<Integer> periods, String version) {
        List<ProductionPlan> plans;
        if (version != null && !version.trim().isEmpty()) {
            plans = productionPlanRepository.findByVersion(version);
        } else if (periods == null || periods.isEmpty()) {
            plans = productionPlanRepository.findAll();
        } else {
            plans = productionPlanRepository.findByYearMonthIn(periods);
        }
        Map<String, Set<String>> sharedMoldGroups = buildSharedMoldGroups();
        SharedMoldEquipmentContext sharedMoldContext = buildSharedMoldEquipmentContext(plans, sharedMoldGroups);

        Map<String, EquipmentLoadRow> rowMap = new LinkedHashMap<>();

        for (ProductionPlan p : plans) {
            if (p.getEquipment() == null) continue;
            if (p.getCycleTime() == null) continue;
            if (p.getPlanQty() == null) continue;

            String department = defaultText(p.getManufacturingDepartment(), "—");
            EquipmentCatalog catalog = equipmentCatalogRepository
                    .findByManufacturingDepartmentAndEquipmentModel(p.getManufacturingDepartment(), p.getEquipment())
                    .orElse(null);

            String equipmentCategory = catalog != null
                    ? catalog.getEquipmentCategory()
                    : defaultText(p.getProcess(), defaultText(p.getEquipment(), "—"));
            String equipmentBrand = catalog != null ? catalog.getEquipmentBrand() : "—";
            String equipmentModel = catalog != null
                    ? catalog.getEquipmentModel()
                    : defaultText(p.getEquipment(), "—");
            int equipmentCount = catalog != null && catalog.getEquipmentCount() != null
                    ? catalog.getEquipmentCount()
                    : 1;
            boolean matchedCatalog = catalog != null;
            String sharedMoldKey = isSharedMoldSelfProductPlan(p, sharedMoldGroups) ? buildSharedMoldKey(p, sharedMoldGroups) : null;
            boolean sharedMoldAdjusted = sharedMoldKey != null && sharedMoldContext.adjustedKeys.contains(sharedMoldKey);
            boolean sharedMoldSuppressed = sharedMoldContext.suppressedPlans.contains(p);

            String key = department + "|" + equipmentCategory + "|" + equipmentModel + "|" + p.getYearMonth();
            EquipmentLoadRow row = rowMap.computeIfAbsent(key, k -> {
                EquipmentLoadRow r = new EquipmentLoadRow();
                r.setManufacturingDepartment(department);
                r.setEquipmentCategory(equipmentCategory);
                r.setEquipmentBrand(equipmentBrand);
                r.setEquipmentModel(equipmentModel);
                r.setEquipmentCount(equipmentCount);
                r.setMatchedCatalog(matchedCatalog);
                r.setEquipment(p.getEquipment());
                r.setProcess(p.getProcess());
                r.setYearMonth(p.getYearMonth());
                r.setTaskTimeHours(0.0);
                r.setAvailableTimeHours(0.0);
                r.setUtilizationRate(0.0);
                r.setStatus("LOOSE");
                r.setWorkDays(0.0);
                r.setDailyHours(DEFAULT_HOURS_PER_DAY);
                r.setPlanQty(0.0);
                r.setCycleTime(0.0);
                r.setMoldCavity(1);
                r.setRequiredSeconds(0.0);
                r.setAvailableSecondsPerMachine(0.0);
                r.setAvailableSecondsTotal(0.0);
                r.setRequiredMachineCount(0.0);
                r.setDifference(0.0);
                r.setLoadRate(0.0);
                r.setSharedMoldAdjusted(sharedMoldAdjusted);
                r.setSharedMoldSuppressed(sharedMoldSuppressed);
                r.setSharedMoldGroupKey(sharedMoldAdjusted ? sharedMoldGroups.get(p.getItemCode()).stream().sorted().collect(Collectors.joining("|")) : null);
                return r;
            });

            double workDays = operatingDaysRepository.findByYearMonth(p.getYearMonth())
                    .map(od -> od.getWorkDays()).orElse(0.0);
            double availableSeconds = workDays * DEFAULT_HOURS_PER_DAY * 3600.0;
            int moldCavity = (p.getMoldCavity() == null || p.getMoldCavity() <= 0) ? 1 : p.getMoldCavity();
            double requiredSeconds = sharedMoldSuppressed ? 0.0 : p.getPlanQty() * p.getCycleTime() / moldCavity;

            row.setWorkDays(workDays);
            row.setDailyHours(DEFAULT_HOURS_PER_DAY);
            row.setPlanQty(row.getPlanQty() + p.getPlanQty());
            row.setCycleTime(p.getCycleTime());
            row.setMoldCavity(moldCavity);
            row.setRequiredSeconds(row.getRequiredSeconds() + requiredSeconds);
            row.setAvailableSecondsPerMachine(availableSeconds);
            row.setTaskTimeHours(row.getTaskTimeHours() + requiredSeconds / 3600.0);
            row.getDetailRows().add(buildDetailRow(p, moldCavity, sharedMoldAdjusted, sharedMoldSuppressed, sharedMoldContext, sharedMoldGroups));
            if (availableSeconds > 0) {
                row.setRequiredMachineCount(row.getRequiredMachineCount() + requiredSeconds / availableSeconds);
            }
        }

        for (EquipmentLoadRow row : rowMap.values()) {
            double availableTimeHours = row.getWorkDays() * DEFAULT_HOURS_PER_DAY;
            row.setAvailableTimeHours(availableTimeHours);
            row.setAvailableSecondsTotal(row.getAvailableSecondsPerMachine() * row.getEquipmentCount());

            double loadRate = row.getEquipmentCount() != null && row.getEquipmentCount() > 0
                    ? row.getRequiredMachineCount() / row.getEquipmentCount()
                    : 0.0;
            row.setDifference(row.getEquipmentCount() - row.getRequiredMachineCount());
            row.setLoadRate(loadRate);
            row.setUtilizationRate(loadRate);
            row.setEquipment(row.getEquipmentModel());
            row.setStatus(resolveStatus(loadRate));
        }

        List<EquipmentLoadRow> result = new ArrayList<>(rowMap.values());
        result.sort(Comparator.comparingInt(EquipmentLoadRow::getYearMonth)
                .thenComparing(EquipmentLoadRow::getManufacturingDepartment)
                .thenComparing(EquipmentLoadRow::getEquipmentCategory)
                .thenComparing(EquipmentLoadRow::getEquipmentModel));
        return result;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private SharedMoldEquipmentContext buildSharedMoldEquipmentContext(List<ProductionPlan> plans, Map<String, Set<String>> sharedMoldGroups) {
        Map<String, List<ProductionPlan>> grouped = new LinkedHashMap<>();
        for (ProductionPlan plan : plans) {
            if (!isSharedMoldSelfProductPlan(plan, sharedMoldGroups)) {
                continue;
            }
            grouped.computeIfAbsent(buildSharedMoldKey(plan, sharedMoldGroups), ignored -> new ArrayList<>()).add(plan);
        }

        Set<String> adjustedKeys = new HashSet<>();
        Set<ProductionPlan> suppressedPlans = new HashSet<>();
        Map<String, String> activePeerByKey = new HashMap<>();
        for (Map.Entry<String, List<ProductionPlan>> entry : grouped.entrySet()) {
            List<ProductionPlan> groupRows = entry.getValue();
            if (groupRows.size() < 2) {
                continue;
            }
            adjustedKeys.add(entry.getKey());
            ProductionPlan activePlan = groupRows.stream()
                    .max(Comparator.comparingDouble(plan -> toNumber(plan.getPlanQty())))
                    .orElse(null);
            double maxPlanQty = activePlan == null ? 0.0 : toNumber(activePlan.getPlanQty());
            activePeerByKey.put(entry.getKey(), activePlan != null ? activePlan.getItemCode() : null);
            for (ProductionPlan plan : groupRows) {
                if (toNumber(plan.getPlanQty()) < maxPlanQty) {
                    suppressedPlans.add(plan);
                }
            }
        }
        return new SharedMoldEquipmentContext(adjustedKeys, suppressedPlans, activePeerByKey);
    }

    private boolean isSharedMoldSelfProductPlan(ProductionPlan plan, Map<String, Set<String>> sharedMoldGroups) {
        if (plan == null) return false;
        String itemCode = plan.getItemCode();
        return itemCode != null
                && sharedMoldGroups.containsKey(itemCode);
    }

    private String buildSharedMoldKey(ProductionPlan plan, Map<String, Set<String>> sharedMoldGroups) {
        Set<String> group = sharedMoldGroups.get(plan.getItemCode());
        String groupKey = group.stream().sorted().collect(Collectors.joining("|"));
        return plan.getYearMonth() + "|" + defaultText(plan.getEquipment(), "—") + "|" +
                defaultText(plan.getProcess(), "—") + "|" +
                Objects.requireNonNullElse(plan.getMoldCavity(), 1) + "|" +
                defaultText(plan.getManufacturingDepartment(), "—") + "|" + groupKey;
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

    private double toNumber(Double value) {
        return Objects.requireNonNullElse(value, 0.0);
    }

    private EquipmentLoadDetailRow buildDetailRow(
            ProductionPlan plan,
            int moldCavity,
            boolean sharedMoldAdjusted,
            boolean sharedMoldSuppressed,
            SharedMoldEquipmentContext context,
            Map<String, Set<String>> sharedMoldGroups
    ) {
        EquipmentLoadDetailRow row = new EquipmentLoadDetailRow();
        row.setItemCode(plan.getItemCode());
        row.setFinishedProductCode(plan.getFinishedProductCode());
        row.setYearMonth(plan.getYearMonth());
        row.setProcess(plan.getProcess());
        row.setEquipment(plan.getEquipment());
        row.setMoldCavity(moldCavity);
        row.setPlanQty(plan.getPlanQty());
        row.setCycleTime(plan.getCycleTime());
        double rawSeconds = toNumber(plan.getPlanQty()) * toNumber(plan.getCycleTime()) / moldCavity;
        row.setRequiredSecondsRaw(rawSeconds);
        row.setRequiredSecondsEffective(sharedMoldSuppressed ? 0.0 : rawSeconds);
        row.setSharedMoldAdjusted(sharedMoldAdjusted);
        row.setSharedMoldSuppressed(sharedMoldSuppressed);
        if (sharedMoldAdjusted) {
            row.setSharedMoldGroupKey(sharedMoldGroups.get(plan.getItemCode()).stream().sorted().collect(Collectors.joining("|")));
            row.setSharedMoldPeerItemCode(context.activePeerByKey.get(buildSharedMoldKey(plan, sharedMoldGroups)));
        }
        return row;
    }

    private static class SharedMoldEquipmentContext {
        private final Set<String> adjustedKeys;
        private final Set<ProductionPlan> suppressedPlans;
        private final Map<String, String> activePeerByKey;

        private SharedMoldEquipmentContext(Set<String> adjustedKeys, Set<ProductionPlan> suppressedPlans, Map<String, String> activePeerByKey) {
            this.adjustedKeys = adjustedKeys;
            this.suppressedPlans = suppressedPlans;
            this.activePeerByKey = activePeerByKey;
        }
    }

    private String resolveStatus(double rate) {
        if (rate >= 1.0) return "OVERLOADED";
        if (rate >= 0.85) return "TIGHT";
        if (rate >= 0.50) return "NORMAL";
        return "LOOSE";
    }
}
