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

    /** 兼容旧无版本调用（全量删除重算） */
    public void calculate() {
        CalculateRequest req = new CalculateRequest();
        req.setDemandVersion(null);
        req.setBomVersion(null);
        req.setSafetyStockVersion(null);
        req.setInventoryVersion(null);
        req.setResultVersion("default");
        calculate(req);
    }

    public void calculate(CalculateRequest req) {
        log.info("=== APS Plan Calculation Start, resultVersion={} ===", req.getResultVersion());

        // 删除旧的结果版本
        if (req.getResultVersion() != null) {
            productionPlanRepository.deleteByVersion(req.getResultVersion());
        } else {
            productionPlanRepository.deleteAllInBatch();
        }

        // 获取完成品列表和期间列表
        List<String> finishedProducts;
        List<Integer> allPeriods;
        if (req.getDemandVersion() != null) {
            finishedProducts = demandRepository.findDistinctItemCodesByVersion(req.getDemandVersion());
            allPeriods       = demandRepository.findDistinctYearMonthsByVersion(req.getDemandVersion());
        } else {
            finishedProducts = demandRepository.findDistinctItemCodes();
            allPeriods       = demandRepository.findDistinctYearMonths();
        }
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
                if (req.getDemandVersion() != null) {
                    dOpt = demandRepository.findFirstByItemCodeAndYearMonthAndVersion(fp, period, req.getDemandVersion());
                } else {
                    dOpt = demandRepository.findFirstByItemCodeAndYearMonth(fp, period);
                }
                if (!dOpt.isPresent()) continue;

                double netDemand = dOpt.get().getNetDemand() != null ? dOpt.get().getNetDemand() : 0.0;
                if (netDemand <= 0) continue;

                processNode(fp, fp, netDemand, true, period,
                        crossMonthInventory, remainingInventory, safetyStockAdded,
                        periodTotalPlanQty, periodScrapRate, periodGrossDemand,
                        req, batch);
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
            CalculateRequest req,
            List<ProductionPlan> batch) {

        // 从 BOM 获取制造信息
        Optional<Bom> bomOpt;
        if (req.getBomVersion() != null) {
            bomOpt = bomRepository.findFirstByParentCodeAndVersion(itemCode, req.getBomVersion());
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
            // 完成品层：直接用 netDemand 计算
            double oneMinusScrap = 1.0 - scrapRate;
            planQty = oneMinusScrap > 0 ? Math.ceil(grossDemand / oneMinusScrap) : 0.0;
        } else {
            // 半成品层：初始化期初库存
            if (!remainingInventory.containsKey(itemCode)) {
                double initInv;
                if (crossMonthInventory.containsKey(itemCode)) {
                    initInv = crossMonthInventory.get(itemCode);
                } else if (req.getInventoryVersion() != null) {
                    initInv = inventoryCountRepository
                            .findFirstByItemCodeAndVersion(itemCode, req.getInventoryVersion())
                            .map(InventoryCount::getAvailableQty).orElse(0.0);
                } else {
                    initInv = inventoryCountRepository
                            .findFirstByItemCodeOrderByYearMonthDesc(itemCode)
                            .map(InventoryCount::getAvailableQty).orElse(0.0);
                }
                remainingInventory.put(itemCode, initInv);
            }

            double inventory = remainingInventory.get(itemCode);
            currentInventory = inventory;

            // 安全库存（同月只加一次）
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
                        currentInventory, grossDemand, safetyDaysRecorded, scrapRate, planQty, req.getResultVersion());
                periodTotalPlanQty.merge(itemCode, planQty, Double::sum);
                periodScrapRate.putIfAbsent(itemCode, scrapRate);
                // 净需求 <= 0，剪枝，不展开子件
                return;
            } else {
                double oneMinusScrap = 1.0 - scrapRate;
                planQty = oneMinusScrap > 0 ? Math.ceil(netDemand / oneMinusScrap) : 0.0;
                remainingInventory.put(itemCode, 0.0);
            }
        }

        saveRecord(batch, finishedProduct, itemCode, period,
                process, equipment, moldCavity, cycleTime, staffCount, taktTime,
                currentInventory, grossDemand, safetyDaysRecorded, scrapRate, planQty, req.getResultVersion());

        periodTotalPlanQty.merge(itemCode, planQty, Double::sum);
        periodScrapRate.putIfAbsent(itemCode, scrapRate);

        // planQty > 0 时向下展开子件
        if (planQty > 0) {
            List<Bom> children;
            if (req.getBomVersion() != null) {
                children = bomRepository.findByParentCodeAndVersion(itemCode, req.getBomVersion());
            } else {
                children = bomRepository.findByParentCode(itemCode);
            }
            for (Bom child : children) {
                if (child.getChildCode() == null || child.getChildCode().isEmpty()) continue;
                double childGross = planQty * (child.getUsageQty() != null ? child.getUsageQty() : 0.0);
                processNode(child.getChildCode(), finishedProduct, childGross, false, period,
                        crossMonthInventory, remainingInventory, safetyStockAdded,
                        periodTotalPlanQty, periodScrapRate, periodGrossDemand,
                        req, batch);
            }
        }
    }

    /**
     * 查询安全库存数量：先按 itemCode+yearMonth+version 精确查，找不到则按 itemCode+version 取任意
     */
    private double getSafetyStockQty(String itemCode, Integer yearMonth, CalculateRequest req) {
        Optional<SafetyStock> ssOpt = Optional.empty();

        if (req.getSafetyStockVersion() != null) {
            ssOpt = safetyStockRepository.findByItemCodeAndYearMonthAndVersion(
                    itemCode, yearMonth, req.getSafetyStockVersion());
            if (!ssOpt.isPresent()) {
                ssOpt = safetyStockRepository.findFirstByItemCodeAndVersion(
                        itemCode, req.getSafetyStockVersion());
            }
        } else {
            ssOpt = safetyStockRepository.findByItemCode(itemCode).stream()
                    .filter(s -> yearMonth.equals(s.getYearMonth()))
                    .findFirst();
            if (!ssOpt.isPresent()) {
                ssOpt = safetyStockRepository.findByItemCode(itemCode).stream().findFirst();
            }
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
