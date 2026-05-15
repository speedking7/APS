package com.aps.service;

import com.aps.dto.CalculateRequest;
import com.aps.entity.*;
import com.aps.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public void calculate(CalculateRequest req) {
        if (req == null || req.getVersion() == null || req.getVersion().isBlank()) {
            throw new IllegalArgumentException("请选择基础数据版本");
        }
        String version = req.getVersion().trim();
        String resultVersion = version;
        log.info("=== APS Plan Calculation Start, version={}, resultVersion={} ===", version, resultVersion);

        // 结果版本与基础版本保持一致，避免结果页和计算输入脱节
        productionPlanRepository.deleteByVersion(resultVersion);

        // 仅按用户选择的单一版本获取全部基础数据
        List<String> finishedProducts = demandRepository.findDistinctItemCodesByVersion(version);
        List<Integer> allPeriods       = demandRepository.findDistinctYearMonthsByVersion(version);
        log.info("Finished products: {}, periods: {}", finishedProducts.size(), allPeriods);

        // 跨月期末库存：key=itemCode, value=期末库存量
        Map<String, Double> crossMonthInventory = new HashMap<>();

        for (Integer period : allPeriods) {
            log.info("--- Processing period: {} ---", period);

            // 当月状态
            Map<String, Double> remainingInventory = new HashMap<>();   // 当月期初库存（随消耗递减）
            Set<String>         safetyStockAdded   = new HashSet<>();   // 已加过安全库存的物料
            Map<String, Double> periodTotalPlanQty = new HashMap<>();   // 当月各物料累计 planQty
            Map<String, Double> periodScrapRate    = new HashMap<>();   // 当月各物料 scrapRate
            Map<String, Double> periodGrossDemand  = new HashMap<>();   // 当月各物料累计毛需求

            List<ProductionPlan> batch = new ArrayList<>();

            for (String fp : finishedProducts) {
                Optional<Demand> dOpt;
                dOpt = demandRepository.findFirstByItemCodeAndYearMonthAndVersion(fp, period, version);
                if (!dOpt.isPresent()) continue;

                double netDemand = dOpt.get().getNetDemand() != null ? dOpt.get().getNetDemand() : 0.0;
                if (netDemand <= 0) continue;

                processNode(fp, fp, netDemand, true, period,
                        crossMonthInventory, remainingInventory, safetyStockAdded,
                        periodTotalPlanQty, periodScrapRate, periodGrossDemand,
                        new HashSet<>(),
                        req, version, resultVersion, batch);
            }

            productionPlanRepository.saveAll(batch);
            log.info("Period {} saved {} plan rows", period, batch.size());

            // 计算期末库存作为下月期初
            crossMonthInventory = new HashMap<>();
            Set<String> allItems = new HashSet<>();
            allItems.addAll(periodTotalPlanQty.keySet());
            allItems.addAll(periodGrossDemand.keySet());
            for (String itemCode : allItems) {
                double remainInv  = remainingInventory.getOrDefault(itemCode, 0.0);
                double planQtySum = periodTotalPlanQty.getOrDefault(itemCode, 0.0);
                double sr         = periodScrapRate.getOrDefault(itemCode, 0.0);
                double endInv     = Math.max(0.0, remainInv) + planQtySum * (1.0 - sr);
                crossMonthInventory.put(itemCode, endInv);
            }
        }

        log.info("=== APS Plan Calculation Done ===");
    }

    /**
     * DFS 递归处理 BOM 节点
     */
    private void processNode(
            String itemCode,
            String finishedProduct,
            double grossDemand,
            boolean isFinishedProduct,
            Integer period,
            Map<String, Double> crossMonthInventory,
            Map<String, Double> remainingInventory,
            Set<String> safetyStockAdded,
            Map<String, Double> periodTotalPlanQty,
            Map<String, Double> periodScrapRate,
            Map<String, Double> periodGrossDemand,
            Set<String> path,
            CalculateRequest req,
            String version,
            String resultVersion,
            List<ProductionPlan> batch) {

        if (!path.add(itemCode)) {
            return;
        }
        try {
            // 从 BOM 获取制造信息
            Optional<Bom> bomOpt;
            if (version != null) {
                bomOpt = bomRepository.findFirstByParentCodeAndVersion(itemCode, version);
            } else {
                bomOpt = bomRepository.findFirstByParentCode(itemCode);
            }

            String  process    = null;
            String  equipment  = null;
            Integer moldCavity = null;
            Double  cycleTime  = null;
            Double  staffCount = null;
            Double  taktTime   = null;
            double  scrapRate  = 0.0;

            if (bomOpt.isPresent()) {
                Bom b = bomOpt.get();
                process    = b.getProcess();
                equipment  = b.getEquipment();
                moldCavity = b.getMoldCavity();
                cycleTime  = b.getCycleTime();
                staffCount = b.getStaffCount();
                taktTime   = b.getTaktTime();
                scrapRate  = b.getScrapRate() != null ? b.getScrapRate() : 0.0;
            }

            // 累计毛需求
            periodGrossDemand.merge(itemCode, grossDemand, Double::sum);

            double planQty;
            double currentInventory = 0.0;
            double safetyDaysRecorded = 0.0;

            if (isFinishedProduct) {
                double oneMinusScrap = 1.0 - scrapRate;
                planQty = oneMinusScrap > 0 ? Math.ceil(grossDemand / oneMinusScrap) : 0.0;
            } else {
                if (!remainingInventory.containsKey(itemCode)) {
                    double initInv;
                    if (crossMonthInventory.containsKey(itemCode)) {
                        initInv = crossMonthInventory.get(itemCode);
                    } else {
                        initInv = inventoryCountRepository
                                .findFirstByItemCodeAndVersion(itemCode, version)
                                .map(InventoryCount::getAvailableQty).orElse(0.0);
                    }
                    remainingInventory.put(itemCode, initInv);
                }

                double inventory = remainingInventory.get(itemCode);
                currentInventory = inventory;

                double safetyStockQty = 0.0;
                if (!safetyStockAdded.contains(itemCode)) {
                    safetyStockQty = getSafetyStockQty(itemCode, period, req);
                    safetyStockAdded.add(itemCode);
                    safetyDaysRecorded = safetyStockQty;
                }

                double netDemand = grossDemand - inventory + safetyStockQty;

                if (netDemand <= 0) {
                    planQty = 0.0;
                    remainingInventory.put(itemCode, Math.max(0.0, inventory - grossDemand));

                    saveRecord(batch, finishedProduct, itemCode, period,
                            process, equipment, moldCavity, cycleTime, staffCount, taktTime,
                            currentInventory, grossDemand, safetyDaysRecorded, scrapRate, planQty, resultVersion);
                    periodTotalPlanQty.merge(itemCode, planQty, Double::sum);
                    periodScrapRate.putIfAbsent(itemCode, scrapRate);
                    return;
                } else {
                    double oneMinusScrap = 1.0 - scrapRate;
                    planQty = oneMinusScrap > 0 ? Math.ceil(netDemand / oneMinusScrap) : 0.0;
                    remainingInventory.put(itemCode, 0.0);
                }
            }

            saveRecord(batch, finishedProduct, itemCode, period,
                    process, equipment, moldCavity, cycleTime, staffCount, taktTime,
                    currentInventory, grossDemand, safetyDaysRecorded, scrapRate, planQty, resultVersion);

            periodTotalPlanQty.merge(itemCode, planQty, Double::sum);
            periodScrapRate.putIfAbsent(itemCode, scrapRate);

            if (planQty > 0) {
                List<Bom> children;
                if (version != null) {
                    children = bomRepository.findByParentCodeAndVersion(itemCode, version);
                } else {
                    children = bomRepository.findByParentCode(itemCode);
                }
                for (Bom child : children) {
                    if (child.getChildCode() == null || child.getChildCode().isEmpty()) continue;
                    double childGross = planQty * (child.getUsageQty() != null ? child.getUsageQty() : 0.0);
                    processNode(child.getChildCode(), finishedProduct, childGross, false, period,
                            crossMonthInventory, remainingInventory, safetyStockAdded,
                            periodTotalPlanQty, periodScrapRate, periodGrossDemand,
                            path,
                            req, version, resultVersion, batch);
                }
            }
        } finally {
            path.remove(itemCode);
        }
    }

    /**
     * 查询安全库存数量：先按 itemCode+yearMonth+version 精确查，找不到则按 itemCode+version 取任意
     */
    private double getSafetyStockQty(String itemCode, Integer yearMonth, CalculateRequest req) {
        Optional<SafetyStock> ssOpt = Optional.empty();

        ssOpt = safetyStockRepository.findByItemCodeAndYearMonthAndVersion(
                itemCode, yearMonth, req.getVersion());
        if (!ssOpt.isPresent()) {
            ssOpt = safetyStockRepository.findFirstByItemCodeAndVersion(
                    itemCode, req.getVersion());
        }

        if (!ssOpt.isPresent()) return 0.0;
        SafetyStock ss = ssOpt.get();
        double safetyDays = ss.getSafetyDays() != null ? ss.getSafetyDays() : 0.0;
        if (ss.getDailyEquivalent() != null) {
            return ss.getDailyEquivalent() * safetyDays;
        }
        return safetyDays;
    }

    private void saveRecord(
            List<ProductionPlan> batch,
            String finishedProduct, String itemCode, Integer period,
            String process, String equipment, Integer moldCavity,
            Double cycleTime, Double staffCount, Double taktTime,
            double currentInventory, double forecast, double safetyDays,
            double scrapRate, double planQty, String version) {

        ProductionPlan plan = new ProductionPlan();
        plan.setFinishedProductCode(finishedProduct);
        plan.setItemCode(itemCode);
        plan.setYearMonth(period);
        plan.setProcess(process);
        plan.setEquipment(equipment);
        plan.setMoldCavity(moldCavity);
        plan.setCycleTime(cycleTime);
        plan.setStaffCount(staffCount);
        plan.setTaktTime(taktTime);
        plan.setCurrentInventory(currentInventory);
        plan.setForecast(forecast);
        plan.setSafetyDays(safetyDays);
        plan.setScrapRate(scrapRate);
        plan.setIsProduce(planQty > 0 ? "Y" : "N");
        plan.setPlanQty(planQty);
        plan.setVersion(version);
        batch.add(plan);
    }
}
