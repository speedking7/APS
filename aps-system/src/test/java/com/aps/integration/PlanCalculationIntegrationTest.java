package com.aps.integration;

import com.aps.entity.*;
import com.aps.repository.*;
import com.aps.service.PlanCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * PlanCalculationService 端到端集成测试（H2 内存数据库）
 *
 * 验证完整计算流程：写入基础数据 → 触发计算 → 校验 t_production_plan 结果
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PlanCalculationIntegrationTest {

    @Autowired private PlanCalculationService planCalculationService;

    @Autowired private ForecastRepository forecastRepository;
    @Autowired private ScrapRateRepository scrapRateRepository;
    @Autowired private InventoryDaysRepository inventoryDaysRepository;
    @Autowired private OperatingDaysRepository operatingDaysRepository;
    @Autowired private BomRepository bomRepository;
    @Autowired private InventoryCountRepository inventoryCountRepository;
    @Autowired private ProductionPlanRepository productionPlanRepository;

    @BeforeEach
    void cleanUp() {
        productionPlanRepository.deleteAllInBatch();
        forecastRepository.deleteAllInBatch();
        scrapRateRepository.deleteAllInBatch();
        inventoryDaysRepository.deleteAllInBatch();
        operatingDaysRepository.deleteAllInBatch();
        bomRepository.deleteAllInBatch();
        inventoryCountRepository.deleteAllInBatch();
    }

    // =========================================================================
    // 场景 1：单品单期，无 BOM 子件
    // =========================================================================

    @Test
    void singleProductSinglePeriod_correctPlanQtyAndIsProduce() {
        // 基础数据
        forecastRepository.save(new Forecast(null, "P001", 202601, 100.0, null));
        scrapRateRepository.save(new ScrapRate(null, "P001", 0.1));
        inventoryDaysRepository.save(new InventoryDays(null, "P001", 7.0, 30.0));
        operatingDaysRepository.save(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0));
        inventoryCountRepository.save(new InventoryCount(null, "P001", 202512, 0.0, null));

        // 触发计算
        planCalculationService.calculate();

        // 验证结果
        List<ProductionPlan> plans = productionPlanRepository.findAll();
        assertThat(plans).hasSize(1);

        ProductionPlan plan = plans.get(0);
        assertThat(plan.getItemCode()).isEqualTo("P001");
        assertThat(plan.getFinishedProductCode()).isEqualTo("P001");
        assertThat(plan.getYearMonth()).isEqualTo(202601);
        assertThat(plan.getForecast()).isEqualTo(100.0);
        assertThat(plan.getCurrentInventory()).isEqualTo(0.0);
        assertThat(plan.getIsProduce()).isEqualTo("Y");
        // planQty = (100/22*7 + 100) / 0.9 ≈ 146.46
        assertThat(plan.getPlanQty()).isCloseTo(146.46, within(0.01));
        // 辅助字段
        assertThat(plan.getScrapRate()).isEqualTo(0.1);
        assertThat(plan.getSafetyDays()).isEqualTo(7.0);
        assertThat(plan.getOperatingDays()).isEqualTo(22.0);
    }

    // =========================================================================
    // 场景 2：有期初库存，库存充足 → isProduce=N, planQty=0
    // =========================================================================

    @Test
    void sufficientInventory_planQtyZeroAndIsProduceN() {
        forecastRepository.save(new Forecast(null, "P002", 202601, 10.0, null));
        scrapRateRepository.save(new ScrapRate(null, "P002", 0.0));
        inventoryDaysRepository.save(new InventoryDays(null, "P002", 0.0, null));
        operatingDaysRepository.save(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0));
        inventoryCountRepository.save(new InventoryCount(null, "P002", 202512, 100.0, null));

        planCalculationService.calculate();

        ProductionPlan plan = productionPlanRepository.findAll().get(0);
        assertThat(plan.getPlanQty()).isEqualTo(0.0);
        assertThat(plan.getIsProduce()).isEqualTo("N");
        assertThat(plan.getCurrentInventory()).isEqualTo(100.0);
    }

    // =========================================================================
    // 场景 3：两期跨期库存结转
    // =========================================================================

    @Test
    void twoPeriods_secondPeriodUsesCarryoverInventory() {
        forecastRepository.save(new Forecast(null, "P003", 202601, 100.0, null));
        forecastRepository.save(new Forecast(null, "P003", 202602, 120.0, null));
        scrapRateRepository.save(new ScrapRate(null, "P003", 0.1));
        inventoryDaysRepository.save(new InventoryDays(null, "P003", 7.0, null));
        operatingDaysRepository.save(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0));
        operatingDaysRepository.save(new OperatingDays(null, 202602, 22.0, 22.0, 0.0, 0.0));
        inventoryCountRepository.save(new InventoryCount(null, "P003", 202512, 0.0, null));

        planCalculationService.calculate();

        List<ProductionPlan> plans = productionPlanRepository.findByFinishedProductCode("P003");
        assertThat(plans).hasSize(2);

        plans.sort((a, b) -> a.getYearMonth() - b.getYearMonth());
        ProductionPlan p1 = plans.get(0);
        ProductionPlan p2 = plans.get(1);

        assertThat(p1.getYearMonth()).isEqualTo(202601);
        assertThat(p1.getPlanQty()).isCloseTo(146.46, within(0.01));

        // 结转库存 = 146.46*(1-0.1) - 100 ≈ 31.82
        assertThat(p2.getYearMonth()).isEqualTo(202602);
        assertThat(p2.getCurrentInventory()).isCloseTo(31.82, within(0.01));
        assertThat(p2.getPlanQty()).isCloseTo(140.40, within(0.1));
    }

    // =========================================================================
    // 场景 4：父件 + 子件（BOM 两层）
    // =========================================================================

    @Test
    void bomTwoLevel_childDemandDerivedFromParentPlanQty() {
        // P004 → C004（用量=2）
        forecastRepository.save(new Forecast(null, "P004", 202601, 100.0, null));
        scrapRateRepository.save(new ScrapRate(null, "P004", 0.1));
        inventoryDaysRepository.save(new InventoryDays(null, "P004", 7.0, null));
        operatingDaysRepository.save(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0));
        inventoryCountRepository.save(new InventoryCount(null, "P004", 202512, 0.0, null));
        inventoryCountRepository.save(new InventoryCount(null, "C004", 202512, 0.0, null));
        // P004→C004 的用量关系（工序信息不放在此行）
        bomRepository.save(new Bom(null, "P004", "C004", 2.0, null, null, null, null, null, null, null, null));
        // C004 作为"父零件"的叶节点行（childCode=null）：存储 C004 自身的工序信息
        bomRepository.save(new Bom(null, "C004", null, 0.0, "PROC-A", "EQ-A", 2, 20.0, 1.0, 10.0, null, null));

        planCalculationService.calculate();

        List<ProductionPlan> plans = productionPlanRepository.findByFinishedProductCode("P004");
        assertThat(plans).hasSize(2);

        plans.sort((a, b) -> a.getItemCode().compareTo(b.getItemCode())); // C004 < P004
        ProductionPlan childPlan = plans.stream()
                .filter(p -> "C004".equals(p.getItemCode())).findFirst().orElseThrow();
        ProductionPlan parentPlan = plans.stream()
                .filter(p -> "P004".equals(p.getItemCode())).findFirst().orElseThrow();

        double parentQty = parentPlan.getPlanQty();
        assertThat(parentQty).isCloseTo(146.46, within(0.01));

        // 子件需求 = 父件计划数量 * 2
        assertThat(childPlan.getForecast()).isCloseTo(parentQty * 2, within(0.01));
        // 子件无报废率 → planQty ≈ demand
        assertThat(childPlan.getPlanQty()).isCloseTo(parentQty * 2, within(0.01));
        // 工艺信息应回填
        assertThat(childPlan.getProcess()).isEqualTo("PROC-A");
        assertThat(childPlan.getEquipment()).isEqualTo("EQ-A");
    }

    // =========================================================================
    // 场景 5：查询 API — 按期间/按产品过滤
    // =========================================================================

    @Test
    void queryByPeriod_returnsOnlyMatchingPeriod() {
        forecastRepository.save(new Forecast(null, "P005", 202601, 100.0, null));
        forecastRepository.save(new Forecast(null, "P005", 202602, 80.0, null));
        operatingDaysRepository.save(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0));
        operatingDaysRepository.save(new OperatingDays(null, 202602, 20.0, 20.0, 0.0, 0.0));
        inventoryCountRepository.save(new InventoryCount(null, "P005", 202512, 0.0, null));

        planCalculationService.calculate();

        List<ProductionPlan> period1Plans = productionPlanRepository.findByYearMonth(202601);
        List<ProductionPlan> period2Plans = productionPlanRepository.findByYearMonth(202602);

        assertThat(period1Plans).isNotEmpty();
        assertThat(period1Plans).allMatch(p -> p.getYearMonth() == 202601);
        assertThat(period2Plans).isNotEmpty();
        assertThat(period2Plans).allMatch(p -> p.getYearMonth() == 202602);
    }
}
