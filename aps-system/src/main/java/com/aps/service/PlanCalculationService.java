package com.aps.service;

import com.aps.dto.CalculateRequest;
import com.aps.entity.*;
import com.aps.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 计划计算核心服务（新版业务逻辑）
 *
 * 完成品层：planQty = netDemand / (1 - scrapRate)
 * 半成品层：DFS 递推，同月同物料库存逐步消耗，安全库存每月只加一次
 */
@Service
@Transactional
public class PlanCalculationService {

    private static final Logger log = LoggerFactory.getLogger(PlanCalculationService.class);

    @Autowired private DemandRepository         demandRepository;
    @Autowired private SafetyStockRepository    safetyStockRepository;
    @Autowired private BomRepository            bomRepository;
    @Autowired private InventoryCountRepository inventoryCountRepository;
    @Autowired private ProductionPlanRepository productionPlanRepository;
    @Autowired private ProductionPlanBatchRepository productionPlanBatchRepository;
    @Autowired private SharedMoldRuleService    sharedMoldRuleService;

    private static final class BomIndexes {
        private final Map<String, Bom> firstByParentCode;
        private final Map<String, List<Bom>> childrenByParentCode;

        private BomIndexes(
                Map<String, Bom> firstByParentCode,
                Map<String, List<Bom>> childrenByParentCode) {
            this.firstByParentCode = firstByParentCode;
            this.childrenByParentCode = childrenByParentCode;
        }
    }

    private static final class InventorySafetyIndexes {
        private final Map<String, Double> inventoryByItemCode;
        private final Map<String, SafetyStock> exactSafetyByItemAndPeriod;
        private final Map<String, SafetyStock> fallbackSafetyByItemCode;

        private InventorySafetyIndexes(
                Map<String, Double> inventoryByItemCode,
                Map<String, SafetyStock> exactSafetyByItemAndPeriod,
                Map<String, SafetyStock> fallbackSafetyByItemCode) {
            this.inventoryByItemCode = inventoryByItemCode;
            this.exactSafetyByItemAndPeriod = exactSafetyByItemAndPeriod;
            this.fallbackSafetyByItemCode = fallbackSafetyByItemCode;
        }
    }

    private static final class NodeRequest {
        private final String itemCode;
        private final String finishedProduct;
        private final double grossDemand;
        private final boolean finishedProductNode;
        private final Integer period;
        private final BomIndexes bomIndexes;
        private final Demand finishedProductDemand;
        private final Bom incomingBom;
        private final Set<String> ancestorPath;

        private NodeRequest(
                String itemCode,
                String finishedProduct,
                double grossDemand,
                boolean finishedProductNode,
                Integer period,
                BomIndexes bomIndexes,
                Demand finishedProductDemand,
                Bom incomingBom,
                Set<String> ancestorPath) {
            this.itemCode = itemCode;
            this.finishedProduct = finishedProduct;
            this.grossDemand = grossDemand;
            this.finishedProductNode = finishedProductNode;
            this.period = period;
            this.bomIndexes = bomIndexes;
            this.finishedProductDemand = finishedProductDemand;
            this.incomingBom = incomingBom;
            this.ancestorPath = ancestorPath;
        }
    }

    private static final class NodeResult {
        private final NodeRequest request;
        private final String process;
        private final String equipment;
        private final String manufacturingDepartment;
        private final String manufacturingUnit;
        private final Integer moldCavity;
        private final Double cycleTime;
        private final Double staffCount;
        private final Double taktTime;
        private final String partAttribute;
        private final double currentInventory;
        private final double recordedGrossDemand;
        private final double safetyDaysRecorded;
        private final double scrapRate;
        private final double rawPlanQty;
        private final double baseEndingInventory;
        private double adjustedPlanQty;
        private double endingInventoryForNextPeriod;

        private NodeResult(
                NodeRequest request,
                String process,
                String equipment,
                String manufacturingDepartment,
                String manufacturingUnit,
                Integer moldCavity,
                Double cycleTime,
                Double staffCount,
                Double taktTime,
                String partAttribute,
                double currentInventory,
                double recordedGrossDemand,
                double safetyDaysRecorded,
                double scrapRate,
                double rawPlanQty,
                double baseEndingInventory) {
            this.request = request;
            this.process = process;
            this.equipment = equipment;
            this.manufacturingDepartment = manufacturingDepartment;
            this.manufacturingUnit = manufacturingUnit;
            this.moldCavity = moldCavity;
            this.cycleTime = cycleTime;
            this.staffCount = staffCount;
            this.taktTime = taktTime;
            this.partAttribute = partAttribute;
            this.currentInventory = currentInventory;
            this.recordedGrossDemand = recordedGrossDemand;
            this.safetyDaysRecorded = safetyDaysRecorded;
            this.scrapRate = scrapRate;
            this.rawPlanQty = rawPlanQty;
            this.baseEndingInventory = baseEndingInventory;
            this.adjustedPlanQty = rawPlanQty;
            this.endingInventoryForNextPeriod = baseEndingInventory + rawPlanQty * Math.max(0.0, 1.0 - scrapRate);
        }
    }

    public void calculate(CalculateRequest req) {
        calculate(req, CalculationProgressListener.noop());
    }

    public void calculate(CalculateRequest req, CalculationProgressListener progressListener) {
        if (req == null || req.getVersion() == null || req.getVersion().isBlank()) {
            throw new IllegalArgumentException("请选择基础数据版本");
        }
        String version = req.getVersion().trim();
        String resultVersion = resolveResultVersion(req, version);
        LocalDateTime calculatedAt = LocalDateTime.now();
        CalculationProgressListener listener = progressListener != null ? progressListener : CalculationProgressListener.noop();
        log.info("=== APS Plan Calculation Start, version={}, resultVersion={} ===", version, resultVersion);
        listener.onProgress(5, "初始化", null, null, "开始计算");

        // 同结果版本重算前先清空旧结果，避免保留历史脏数据
        productionPlanRepository.deleteByVersion(resultVersion);
        listener.onProgress(10, "清理旧结果", null, null, "已清理旧结果");

        // 仅按用户选择的单一版本获取全部基础数据
        List<String> finishedProducts = demandRepository.findDistinctItemCodesByVersion(version);
        List<Integer> allPeriods       = demandRepository.findDistinctYearMonthsByVersion(version);
        InventorySafetyIndexes inventorySafetyIndexes = buildInventorySafetyIndexes(version);
        Map<String, Set<String>> sharedMoldGroups = buildSharedMoldGroups();
        log.info("Finished products: {}, periods: {}", finishedProducts.size(), allPeriods);
        listener.onProgress(20, "加载基础数据", null, allPeriods.size(), "基础数据加载完成");

        // 跨月期末库存：key=itemCode, value=期末库存量
        Map<String, Double> crossMonthInventory = new HashMap<>();

        for (int periodIndex = 0; periodIndex < allPeriods.size(); periodIndex++) {
            Integer period = allPeriods.get(periodIndex);
            log.info("--- Processing period: {} ---", period);
            listener.onProgress(
                    calculatePeriodProgress(periodIndex, allPeriods.size()),
                    "处理期间",
                    period,
                    allPeriods.size(),
                    "正在处理期间 " + period);

            // 当月状态
            Map<String, Double> remainingInventory = new HashMap<>();   // 当月期初库存（随消耗递减）
            Set<String>         safetyStockAdded   = new HashSet<>();   // 已加过安全库存的物料
            Map<String, Double> periodTotalPlanQty = new HashMap<>();   // 当月各物料累计 planQty
            Map<String, Double> periodScrapRate    = new HashMap<>();   // 当月各物料 scrapRate
            Map<String, Double> periodGrossDemand  = new HashMap<>();   // 当月各物料累计毛需求
            Map<String, Double> periodEndingInventory = new HashMap<>(); // 当月理论结余（供下月使用）

            List<ProductionPlan> batch = new ArrayList<>();
            List<NodeRequest> currentLevel = new ArrayList<>();
            for (String fp : finishedProducts) {
                Optional<Demand> dOpt = demandRepository.findFirstByItemCodeAndYearMonthAndVersion(fp, period, version);
                if (!dOpt.isPresent()) continue;

                Demand demand = dOpt.get();
                double netDemand = calculateFinishedProductNetDemand(fp, period, demand, crossMonthInventory);
                BomIndexes bomIndexes = buildBomIndexes(version, fp);
                currentLevel.add(new NodeRequest(
                        fp,
                        fp,
                        netDemand,
                        true,
                        period,
                        bomIndexes,
                        demand,
                        null,
                        Collections.emptySet()
                ));
            }

            while (!currentLevel.isEmpty()) {
                List<NodeResult> levelResults = new ArrayList<>();
                for (NodeRequest request : currentLevel) {
                    NodeResult result = computeNodeResult(
                            request,
                            crossMonthInventory,
                            remainingInventory,
                            safetyStockAdded,
                            periodGrossDemand,
                            inventorySafetyIndexes);
                    if (result != null) {
                        levelResults.add(result);
                    }
                }

                applySharedMoldAlignment(levelResults, sharedMoldGroups);

                List<NodeRequest> nextLevel = new ArrayList<>();
                for (NodeResult result : levelResults) {
                    periodTotalPlanQty.merge(result.request.itemCode, result.adjustedPlanQty, Double::sum);
                    periodScrapRate.putIfAbsent(result.request.itemCode, result.scrapRate);
                    periodEndingInventory.put(result.request.itemCode, result.endingInventoryForNextPeriod);
                    saveRecord(batch, result.request.finishedProduct, result.request.itemCode, result.request.period,
                            result.process, result.equipment, result.manufacturingDepartment, result.manufacturingUnit,
                            result.moldCavity, result.cycleTime, result.staffCount, result.taktTime, result.partAttribute,
                            result.currentInventory, result.recordedGrossDemand, result.safetyDaysRecorded,
                            result.scrapRate, result.adjustedPlanQty, result.rawPlanQty, resultVersion, calculatedAt);

                    if (result.adjustedPlanQty > 0) {
                        List<Bom> children = result.request.bomIndexes.childrenByParentCode
                                .getOrDefault(result.request.itemCode, Collections.emptyList());
                        Set<String> currentPath = new HashSet<>(result.request.ancestorPath);
                        currentPath.add(result.request.itemCode);
                        for (Bom child : children) {
                            if (child.getChildCode() == null || child.getChildCode().isEmpty()) continue;
                            if (currentPath.contains(child.getChildCode())) continue;
                            double childGross = result.adjustedPlanQty * (child.getUsageQty() != null ? child.getUsageQty() : 0.0);
                            nextLevel.add(new NodeRequest(
                                    child.getChildCode(),
                                    result.request.finishedProduct,
                                    childGross,
                                    false,
                                    period,
                                    result.request.bomIndexes,
                                    null,
                                    child,
                                    currentPath
                            ));
                        }
                    }
                }
                currentLevel = nextLevel;
            }

            productionPlanBatchRepository.bulkInsert(batch);
            log.info("Period {} saved {} plan rows", period, batch.size());

            // 计算期末库存作为下月期初
            crossMonthInventory = new HashMap<>(periodEndingInventory);
        }

        log.info("=== APS Plan Calculation Done ===");
        listener.onProgress(100, "完成", null, allPeriods.size(), "计算完成");
    }

    private String resolveResultVersion(CalculateRequest req, String baseVersion) {
        if (req == null || req.getResultVersion() == null || req.getResultVersion().isBlank()) {
            return baseVersion;
        }
        return req.getResultVersion().trim();
    }

    private int calculatePeriodProgress(int periodIndex, int totalPeriods) {
        if (totalPeriods <= 0) {
            return 90;
        }
        int start = 25;
        int end = 95;
        return start + (int) Math.floor(((periodIndex + 1) * (end - start)) / (double) totalPeriods);
    }

    private NodeResult computeNodeResult(
            NodeRequest request,
            Map<String, Double> crossMonthInventory,
            Map<String, Double> remainingInventory,
            Set<String> safetyStockAdded,
            Map<String, Double> periodGrossDemand,
            InventorySafetyIndexes inventorySafetyIndexes) {
            String itemCode = request.itemCode;
            Optional<Bom> bomOpt = Optional.ofNullable(request.bomIndexes.firstByParentCode.get(itemCode));

            String  process    = null;
            String  equipment  = null;
            String  manufacturingDepartment = null;
            String  manufacturingUnit = null;
            Integer moldCavity = null;
            Double  cycleTime  = null;
            Double  staffCount = null;
            Double  taktTime   = null;
            String  partAttribute = request.incomingBom != null ? request.incomingBom.getPartAttribute() : null;
            double  scrapRate  = 0.0;

            if (bomOpt.isPresent()) {
                Bom b = bomOpt.get();
                process    = b.getProcess();
                equipment  = b.getEquipment();
                manufacturingDepartment = b.getManufacturingDepartment();
                manufacturingUnit = b.getManufacturingUnit();
                moldCavity = b.getMoldCavity();
                cycleTime  = b.getCycleTime();
                staffCount = b.getStaffCount();
                taktTime   = b.getTaktTime();
                if (partAttribute == null && !request.finishedProductNode) {
                    partAttribute = b.getPartAttribute();
                }
                scrapRate  = b.getScrapRate() != null ? b.getScrapRate() : 0.0;
            }

            // 累计毛需求
            periodGrossDemand.merge(itemCode, request.grossDemand, Double::sum);

            double currentInventory = 0.0;
            double safetyDaysRecorded = 0.0;
            double recordedGrossDemand = request.grossDemand;
            double rawPlanQty;
            double baseEndingInventory;

            if (request.finishedProductNode) {
                double availableInventory = resolveFinishedProductAvailableInventory(itemCode, request.finishedProductDemand, crossMonthInventory);
                double rawDemandQty = request.finishedProductDemand != null && request.finishedProductDemand.getDemandQty() != null
                        ? request.finishedProductDemand.getDemandQty()
                        : request.grossDemand;
                currentInventory = availableInventory;
                double oneMinusScrap = 1.0 - scrapRate;
                rawPlanQty = oneMinusScrap > 0 ? Math.ceil(request.grossDemand / oneMinusScrap) : 0.0;
                baseEndingInventory = Math.max(0.0, availableInventory - rawDemandQty);
            } else {
                boolean inventoryFromCarryover = crossMonthInventory.containsKey(itemCode);
                if (!remainingInventory.containsKey(itemCode)) {
                    double initInv;
                    if (inventoryFromCarryover) {
                        initInv = crossMonthInventory.get(itemCode);
                    } else {
                        initInv = inventorySafetyIndexes.inventoryByItemCode.getOrDefault(itemCode, 0.0);
                    }
                    remainingInventory.put(itemCode, initInv);
                }

                double inventory = remainingInventory.get(itemCode);
                currentInventory = inventory;

                double safetyStockQty = 0.0;
                if (!safetyStockAdded.contains(itemCode)) {
                    safetyStockQty = getSafetyStockQty(itemCode, request.period, inventorySafetyIndexes);
                    safetyStockAdded.add(itemCode);
                    safetyDaysRecorded = safetyStockQty;
                }

                double netDemand = request.grossDemand - inventory + safetyStockQty;
                recordedGrossDemand = inventoryFromCarryover
                        ? Math.max(0.0, request.grossDemand - inventory)
                        : request.grossDemand;

                if (netDemand <= 0) {
                    rawPlanQty = 0.0;
                    remainingInventory.put(itemCode, Math.max(0.0, inventory - request.grossDemand));
                    baseEndingInventory = remainingInventory.get(itemCode);
                } else {
                    double oneMinusScrap = 1.0 - scrapRate;
                    rawPlanQty = oneMinusScrap > 0 ? Math.ceil(netDemand / oneMinusScrap) : 0.0;
                    remainingInventory.put(itemCode, 0.0);
                    baseEndingInventory = 0.0;
                }
            }

            return new NodeResult(
                    request,
                    process,
                    equipment,
                    manufacturingDepartment,
                    manufacturingUnit,
                    moldCavity,
                    cycleTime,
                    staffCount,
                    taktTime,
                    partAttribute,
                    currentInventory,
                    recordedGrossDemand,
                    safetyDaysRecorded,
                    scrapRate,
                    rawPlanQty,
                    baseEndingInventory);
    }

    private BomIndexes buildBomIndexes(String version, String rootProductCode) {
        List<Bom> bomRows = bomRepository.findByVersion(version).stream()
                .filter(row -> row.getRootProductCode() == null || rootProductCode.equals(row.getRootProductCode()))
                .collect(java.util.stream.Collectors.toList());
        Map<String, Bom> firstByParentCode = new HashMap<>();
        Map<String, List<Bom>> childrenByParentCode = new HashMap<>();
        Map<String, Set<String>> seenChildrenByParentCode = new HashMap<>();

        for (Bom row : bomRows) {
            if (row.getParentCode() == null || row.getParentCode().isBlank()) {
                continue;
            }
            firstByParentCode.putIfAbsent(row.getParentCode(), row);
            if (row.getChildCode() == null || row.getChildCode().isBlank()) {
                continue;
            }
            childrenByParentCode
                    .computeIfAbsent(row.getParentCode(), ignored -> new ArrayList<>());
            Set<String> seenChildren = seenChildrenByParentCode
                    .computeIfAbsent(row.getParentCode(), ignored -> new HashSet<>());
            if (seenChildren.add(row.getChildCode())) {
                childrenByParentCode.get(row.getParentCode()).add(row);
            }
        }

        return new BomIndexes(firstByParentCode, childrenByParentCode);
    }

    private InventorySafetyIndexes buildInventorySafetyIndexes(String version) {
        Map<String, Double> inventoryByItemCode = new HashMap<>();
        for (InventoryCount inventoryCount : inventoryCountRepository.findAllByVersion(version)) {
            if (inventoryCount.getItemCode() == null || inventoryCount.getItemCode().isBlank()) {
                continue;
            }
            inventoryByItemCode.merge(
                    inventoryCount.getItemCode(),
                    inventoryCount.getAvailableQty() != null ? inventoryCount.getAvailableQty() : 0.0,
                    (current, ignored) -> current);
        }

        Map<String, SafetyStock> exactSafetyByItemAndPeriod = new HashMap<>();
        Map<String, SafetyStock> fallbackSafetyByItemCode = new HashMap<>();
        for (SafetyStock safetyStock : safetyStockRepository.findAllByVersion(version)) {
            if (safetyStock.getItemCode() == null || safetyStock.getItemCode().isBlank()) {
                continue;
            }
            if (safetyStock.getYearMonth() != null) {
                exactSafetyByItemAndPeriod.putIfAbsent(
                        buildItemPeriodKey(safetyStock.getItemCode(), safetyStock.getYearMonth()),
                        safetyStock);
            }
            fallbackSafetyByItemCode.putIfAbsent(safetyStock.getItemCode(), safetyStock);
        }

        return new InventorySafetyIndexes(inventoryByItemCode, exactSafetyByItemAndPeriod, fallbackSafetyByItemCode);
    }

    private double resolveFinishedProductAvailableInventory(
            String itemCode,
            Demand demand,
            Map<String, Double> crossMonthInventory) {
        if (crossMonthInventory.containsKey(itemCode)) {
            return crossMonthInventory.get(itemCode);
        }
        if (demand == null || demand.getEndingInventory() == null) {
            return 0.0;
        }
        return demand.getEndingInventory();
    }

    private double calculateFinishedProductNetDemand(
            String itemCode,
            Integer period,
            Demand demand,
            Map<String, Double> crossMonthInventory) {
        double demandQty = demand.getDemandQty() != null ? demand.getDemandQty() : 0.0;
        double minimumSafetyStock = demand.getMinSafetyStock() != null ? demand.getMinSafetyStock() : 0.0;
        double importedEndingInventory = demand.getEndingInventory() != null ? demand.getEndingInventory() : 0.0;

        if (!crossMonthInventory.containsKey(itemCode)) {
            return Math.max(0.0, demandQty + minimumSafetyStock - importedEndingInventory);
        }

        double carryoverInventory = crossMonthInventory.get(itemCode);
        return Math.max(0.0, demandQty + minimumSafetyStock - carryoverInventory);
    }

    /**
     * 查询安全库存数量：先按 itemCode+yearMonth+version 精确查，找不到则按 itemCode+version 取任意
     */
    private double getSafetyStockQty(String itemCode, Integer yearMonth, InventorySafetyIndexes indexes) {
        SafetyStock ss = indexes.exactSafetyByItemAndPeriod.get(buildItemPeriodKey(itemCode, yearMonth));
        if (ss == null) {
            ss = indexes.fallbackSafetyByItemCode.get(itemCode);
        }
        if (ss == null) return 0.0;
        double safetyDays = ss.getSafetyDays() != null ? ss.getSafetyDays() : 0.0;
        if (ss.getDailyEquivalent() != null) {
            return ss.getDailyEquivalent() * safetyDays;
        }
        return safetyDays;
    }

    private String buildItemPeriodKey(String itemCode, Integer yearMonth) {
        return itemCode + "#" + yearMonth;
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

    private void applySharedMoldAlignment(List<NodeResult> results, Map<String, Set<String>> sharedMoldGroups) {
        Map<String, List<NodeResult>> grouped = new LinkedHashMap<>();
        for (NodeResult result : results) {
            Set<String> group = sharedMoldGroups.get(result.request.itemCode);
            if (group == null) {
                result.adjustedPlanQty = result.rawPlanQty;
                result.endingInventoryForNextPeriod = result.baseEndingInventory + result.adjustedPlanQty * Math.max(0.0, 1.0 - result.scrapRate);
                continue;
            }
            String groupKey = group.stream().sorted().collect(java.util.stream.Collectors.joining("|"));
            String key = result.request.period + "|" + groupKey;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
        }

        for (List<NodeResult> groupRows : grouped.values()) {
            if (groupRows.size() < 2) {
                NodeResult only = groupRows.get(0);
                only.adjustedPlanQty = only.rawPlanQty;
                only.endingInventoryForNextPeriod = only.baseEndingInventory + only.adjustedPlanQty * Math.max(0.0, 1.0 - only.scrapRate);
                continue;
            }
            double maxRawPlanQty = groupRows.stream()
                    .mapToDouble(result -> result.rawPlanQty)
                    .max()
                    .orElse(0.0);
            for (NodeResult row : groupRows) {
                row.adjustedPlanQty = maxRawPlanQty;
                row.endingInventoryForNextPeriod = row.baseEndingInventory + row.adjustedPlanQty * Math.max(0.0, 1.0 - row.scrapRate);
            }
        }
    }

    private void saveRecord(
            List<ProductionPlan> batch,
            String finishedProduct, String itemCode, Integer period,
            String process, String equipment, String manufacturingDepartment,
            String manufacturingUnit, Integer moldCavity,
            Double cycleTime, Double staffCount, Double taktTime, String partAttribute,
            double currentInventory, double forecast, double safetyDays,
            double scrapRate, double planQty, double rawPlanQty, String version,
            LocalDateTime calculatedAt) {

        ProductionPlan plan = new ProductionPlan();
        plan.setFinishedProductCode(finishedProduct);
        plan.setItemCode(itemCode);
        plan.setYearMonth(period);
        plan.setProcess(process);
        plan.setEquipment(equipment);
        plan.setManufacturingDepartment(manufacturingDepartment);
        plan.setManufacturingUnit(manufacturingUnit);
        plan.setMoldCavity(moldCavity);
        plan.setCycleTime(cycleTime);
        plan.setStaffCount(staffCount);
        plan.setTaktTime(taktTime);
        plan.setPartAttribute(partAttribute);
        plan.setCurrentInventory(currentInventory);
        plan.setForecast(forecast);
        plan.setSafetyDays(safetyDays);
        plan.setScrapRate(scrapRate);
        plan.setIsProduce(planQty > 0 ? "Y" : "N");
        plan.setPlanQty(planQty);
        plan.setRawPlanQty(rawPlanQty);
        plan.setVersion(version);
        plan.setCalculatedAt(calculatedAt);
        batch.add(plan);
    }
}
