package com.aps.service;

import com.aps.entity.*;
import com.aps.repository.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 计划计算核心服务
 *
 * 计算逻辑：
 * 1) 遍历每个完成品（来自预测的 distinct itemCode）
 * 2) 对每个完成品按年月升序处理
 * 3) BOM树展开（父→子→孙），从上到下计算每个节点
 *
 * 节点指标：
 *   - 当前库存：第一期取最新盘点，后续期 = 上期planQty*(1-上期scrapRate) - 上期demand + 上期currentInventory
 *   - 需求：根节点取预测；子件 = 父件本期 planQty * 用量
 *   - 计划数量 = (需求/稼动天数*安全天数 + 需求 - 当前库存) / (1 - 报废率)；负值取 0
 *   - 是否生产：需求 > 当前库存 → Y，否则 N
 */
@Service
@Transactional
public class PlanCalculationService {

    private static final Logger log = LoggerFactory.getLogger(PlanCalculationService.class);

    @Autowired private ForecastRepository forecastRepository;
    @Autowired private ScrapRateRepository scrapRateRepository;
    @Autowired private InventoryDaysRepository inventoryDaysRepository;
    @Autowired private OperatingDaysRepository operatingDaysRepository;
    @Autowired private BomRepository bomRepository;
    @Autowired private InventoryCountRepository inventoryCountRepository;
    @Autowired private ProductionPlanRepository productionPlanRepository;

    /**
     * 全量重算：清空 t_production_plan 后按上述逻辑生成计划
     */
    public void calculate() {
        log.info("=== APS Plan Calculation Start ===");

        // 1. 清空结果表
        productionPlanRepository.deleteAllInBatch();

        // 2. 获取所有完成品 & 所有期间
        List<String> finishedProducts = forecastRepository.findDistinctItemCodes();
        List<Integer> allPeriods = forecastRepository.findDistinctYearMonths();
        log.info("Finished products: {}, periods: {}", finishedProducts, allPeriods);

        // 3. 对每个完成品按期间顺序计算
        for (String finishedProduct : finishedProducts) {
            log.info("--- Calculating finished product: {} ---", finishedProduct);
            List<BomNode> bomNodes = expandBomTree(finishedProduct);
            log.info("BOM tree for {}: {} nodes", finishedProduct, bomNodes.size());

            Map<String, PlanResult> prevPeriodData = new HashMap<>();

            for (Integer period : allPeriods) {
                // 该完成品在本期间是否有预测
                Optional<Forecast> fcOpt = forecastRepository.findFirstByItemCodeAndYearMonth(finishedProduct, period);
                if (!fcOpt.isPresent()) {
                    continue;
                }
                double rootDemand = fcOpt.get().getQuantity();

                Map<String, PlanResult> currentPeriodData = new HashMap<>();
                double operatingDays = operatingDaysRepository.findByYearMonth(period)
                        .map(OperatingDays::getDays).orElse(0.0);

                for (BomNode node : bomNodes) {
                    double scrapRate = scrapRateRepository.findByItemCode(node.itemCode)
                            .map(ScrapRate::getScrapRate).orElse(0.0);
                    double safetyDays = inventoryDaysRepository.findByItemCode(node.itemCode)
                            .map(InventoryDays::getSafetyDays).orElse(0.0);

                    // 当前库存
                    double currentInventory;
                    PlanResult prev = prevPeriodData.get(node.itemCode);
                    if (prev == null) {
                        currentInventory = inventoryCountRepository
                                .findFirstByItemCodeOrderByYearMonthDesc(node.itemCode)
                                .map(InventoryCount::getAvailableQty)
                                .orElse(0.0);
                    } else {
                        currentInventory = prev.planQty * (1 - prev.scrapRate)
                                - prev.demand + prev.currentInventory;
                    }

                    // 需求
                    double demand;
                    if (node.parentCode == null) {
                        demand = rootDemand;
                    } else {
                        PlanResult parentResult = currentPeriodData.get(node.parentCode);
                        if (parentResult == null) {
                            log.warn("Parent {} not yet calculated for {}, defaulting demand to 0",
                                    node.parentCode, node.itemCode);
                            demand = 0.0;
                        } else {
                            demand = parentResult.planQty * node.usageQty;
                        }
                    }

                    // 计划数量
                    double planQty;
                    double oneMinusScrap = 1.0 - scrapRate;
                    if (oneMinusScrap == 0.0) {
                        planQty = 0.0;
                    } else if (operatingDays > 0) {
                        planQty = (demand / operatingDays * safetyDays + demand - currentInventory) / oneMinusScrap;
                    } else {
                        planQty = (demand - currentInventory) / oneMinusScrap;
                    }
                    if (planQty < 0) planQty = 0.0;
                    planQty = Math.ceil(planQty);

                    String isProduce = demand > currentInventory ? "Y" : "N";

                    // 保存结果
                    ProductionPlan plan = new ProductionPlan();
                    plan.setFinishedProductCode(finishedProduct);
                    plan.setItemCode(node.itemCode);
                    plan.setYearMonth(period);
                    plan.setProcess(node.process);
                    plan.setEquipment(node.equipment);
                    plan.setMoldCavity(node.moldCavity);
                    plan.setCycleTime(node.cycleTime);
                    plan.setStaffCount(node.staffCount);
                    plan.setTaktTime(node.taktTime);
                    plan.setCurrentInventory(currentInventory);
                    plan.setForecast(demand);
                    plan.setSafetyDays(safetyDays);
                    plan.setOperatingDays(operatingDays);
                    plan.setScrapRate(scrapRate);
                    plan.setIsProduce(isProduce);
                    plan.setPlanQty(planQty);
                    productionPlanRepository.save(plan);

                    currentPeriodData.put(node.itemCode,
                            new PlanResult(planQty, scrapRate, demand, currentInventory));
                }

                prevPeriodData = currentPeriodData;
            }
        }

        log.info("=== APS Plan Calculation Done ===");
    }

    /**
     * 展开 BOM 树（深度优先，父在前，子在后）
     * 节点的工序/设备等取自该物料作为 parentCode 的 BOM 行（含叶节点）
     */
    private List<BomNode> expandBomTree(String finishedProduct) {
        List<BomNode> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        // 根节点（usageQty 不适用，置 0）
        BomNode root = buildNode(finishedProduct, null, 0.0);
        result.add(root);
        visited.add(finishedProduct);

        traverse(finishedProduct, result, visited);
        return result;
    }

    private void traverse(String parentCode, List<BomNode> result, Set<String> visited) {
        List<Bom> children = bomRepository.findByParentCode(parentCode);
        for (Bom row : children) {
            String childCode = row.getChildCode();
            if (childCode == null || childCode.isEmpty()) {
                continue; // 当前父零件是叶节点；它本身已在父级递归时加入
            }
            if (visited.contains(childCode)) {
                log.warn("Circular BOM detected: parent={} child={} - skipping",
                        parentCode, childCode);
                continue;
            }
            visited.add(childCode);
            double usage = row.getUsageQty() == null ? 0.0 : row.getUsageQty();
            BomNode node = buildNode(childCode, parentCode, usage);
            result.add(node);
            traverse(childCode, result, visited);
        }
    }

    /**
     * 取 itemCode 作为父零件的第一行作为该物料的工序/设备/工艺信息
     */
    private BomNode buildNode(String itemCode, String parentCode, double usageQty) {
        BomNode node = new BomNode();
        node.itemCode = itemCode;
        node.parentCode = parentCode;
        node.usageQty = usageQty;

        bomRepository.findFirstByParentCode(itemCode).ifPresent(b -> {
            node.process = b.getProcess();
            node.equipment = b.getEquipment();
            node.moldCavity = b.getMoldCavity();
            node.cycleTime = b.getCycleTime();
            node.staffCount = b.getStaffCount();
            node.taktTime = b.getTaktTime();
        });
        return node;
    }

    /** BOM 树节点 */
    @Data
    static class BomNode {
        String itemCode;
        String parentCode;
        double usageQty;
        String process;
        String equipment;
        Integer moldCavity;
        Double cycleTime;
        Double staffCount;
        Double taktTime;
    }

    /** 周期内单条计算结果（用于跨期/跨节点引用） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class PlanResult {
        double planQty;
        double scrapRate;
        double demand;
        double currentInventory;
    }
}
