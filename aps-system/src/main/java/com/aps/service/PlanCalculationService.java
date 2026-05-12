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
 * 1) 遍历每个完成品（来自需求表的 distinct itemCode）
 * 2) 对每个完成品按年月升序处理
 * 3) BOM树展开（父→子→孙），从上到下计算每个节点
 *
 * 节点指标：
 *   - 报废率：从 BOM 行取（每制造工步独立），不再使用独立报废率表
 *   - 安全天数：从 t_safety_stock 取
 *   - 稼动天数：使用 t_operating_days.work_days
 *   - 根节点需求：取 t_demand.net_demand（完成品入库需求数）
 *   - 子件需求：父件本期 planQty × BOM 用量
 *   - planQty = ceil((需求/稼动天数×安全天数 + 需求 - 当前库存) / (1 - 报废率))；负值取0
 */
@Service
@Transactional
public class PlanCalculationService {

    private static final Logger log = LoggerFactory.getLogger(PlanCalculationService.class);

    @Autowired private DemandRepository          demandRepository;
    @Autowired private SafetyStockRepository     safetyStockRepository;
    @Autowired private OperatingDaysRepository   operatingDaysRepository;
    @Autowired private BomRepository             bomRepository;
    @Autowired private InventoryCountRepository  inventoryCountRepository;
    @Autowired private ProductionPlanRepository  productionPlanRepository;

    public void calculate() {
        log.info("=== APS Plan Calculation Start ===");
        productionPlanRepository.deleteAllInBatch();

        List<String>  finishedProducts = demandRepository.findDistinctItemCodes();
        List<Integer> allPeriods       = demandRepository.findDistinctYearMonths();
        log.info("Finished products: {}, periods: {}", finishedProducts, allPeriods);

        for (String fp : finishedProducts) {
            log.info("--- Calculating: {} ---", fp);
            List<BomNode> bomNodes = expandBomTree(fp);
            log.info("BOM tree for {}: {} nodes", fp, bomNodes.size());

            Map<String, PlanResult> prevPeriodData = new HashMap<>();

            for (Integer period : allPeriods) {
                Optional<Demand> dOpt = demandRepository.findFirstByItemCodeAndYearMonth(fp, period);
                if (!dOpt.isPresent()) continue;

                // 完成品根节点需求 = 完成品入库需求数
                double rootDemand  = dOpt.get().getNetDemand() != null ? dOpt.get().getNetDemand() : 0.0;
                double workDays    = operatingDaysRepository.findByYearMonth(period)
                        .map(OperatingDays::getWorkDays).orElse(0.0);

                Map<String, PlanResult> currentPeriodData = new HashMap<>();

                for (BomNode node : bomNodes) {
                    double scrapRate = node.scrapRate;  // 来自 BOM 行
                    double safetyDays = safetyStockRepository.findByItemCode(node.itemCode)
                            .map(SafetyStock::getSafetyDays).orElse(0.0);

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
                        demand = (parentResult != null) ? parentResult.planQty * node.usageQty : 0.0;
                    }

                    // 计划数量
                    double planQty;
                    double oneMinusScrap = 1.0 - scrapRate;
                    if (oneMinusScrap <= 0.0) {
                        planQty = 0.0;
                    } else if (workDays > 0) {
                        planQty = (demand / workDays * safetyDays + demand - currentInventory) / oneMinusScrap;
                    } else {
                        planQty = (demand - currentInventory) / oneMinusScrap;
                    }
                    if (planQty < 0) planQty = 0.0;
                    planQty = Math.ceil(planQty);

                    String isProduce = demand > currentInventory ? "Y" : "N";

                    ProductionPlan plan = new ProductionPlan();
                    plan.setFinishedProductCode(fp);
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
                    plan.setOperatingDays(workDays);
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

    private List<BomNode> expandBomTree(String fp) {
        List<BomNode> result  = new ArrayList<>();
        Set<String>   visited = new HashSet<>();
        BomNode root = buildNode(fp, null, 0.0);
        result.add(root);
        visited.add(fp);
        traverse(fp, result, visited);
        return result;
    }

    private void traverse(String parentCode, List<BomNode> result, Set<String> visited) {
        for (Bom row : bomRepository.findByParentCode(parentCode)) {
            String childCode = row.getChildCode();
            if (childCode == null || childCode.isEmpty()) continue;
            if (visited.contains(childCode)) {
                log.warn("Circular BOM: parent={} child={}", parentCode, childCode);
                continue;
            }
            visited.add(childCode);
            double usage = row.getUsageQty() != null ? row.getUsageQty() : 0.0;
            result.add(buildNode(childCode, parentCode, usage));
            traverse(childCode, result, visited);
        }
    }

    private BomNode buildNode(String itemCode, String parentCode, double usageQty) {
        BomNode node = new BomNode();
        node.itemCode   = itemCode;
        node.parentCode = parentCode;
        node.usageQty   = usageQty;
        bomRepository.findFirstByParentCode(itemCode).ifPresent(b -> {
            node.process    = b.getProcess();
            node.equipment  = b.getEquipment();
            node.moldCavity = b.getMoldCavity();
            node.cycleTime  = b.getCycleTime();
            node.staffCount = b.getStaffCount();
            node.taktTime   = b.getTaktTime();
            node.scrapRate  = b.getScrapRate() != null ? b.getScrapRate() : 0.0;
        });
        return node;
    }

    @Data
    static class BomNode {
        String  itemCode;
        String  parentCode;
        double  usageQty;
        String  process;
        String  equipment;
        Integer moldCavity;
        Double  cycleTime;
        Double  staffCount;
        Double  taktTime;
        double  scrapRate;   // 来自 BOM 行
    }

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
