package com.aps.service;

import com.aps.entity.*;
import com.aps.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PlanCalculationService 单元测试
 *
 * 覆盖业务规则：
 *   planQty = (需求/稼动天数*安全天数 + 需求 - 当前库存) / (1 - 报废率)，负值取0
 *   isProduce = 需求 > 当前库存 ? "Y" : "N"
 *   跨期库存结转 = 上期planQty*(1-上期scrapRate) - 上期demand + 上期currentInventory
 */
@ExtendWith(MockitoExtension.class)
class PlanCalculationServiceTest {

    @InjectMocks
    private PlanCalculationService service;

    @Mock private ForecastRepository forecastRepository;
    @Mock private ScrapRateRepository scrapRateRepository;
    @Mock private InventoryDaysRepository inventoryDaysRepository;
    @Mock private OperatingDaysRepository operatingDaysRepository;
    @Mock private BomRepository bomRepository;
    @Mock private InventoryCountRepository inventoryCountRepository;
    @Mock private ProductionPlanRepository productionPlanRepository;

    @Captor
    private ArgumentCaptor<ProductionPlan> planCaptor;

    // -------------------------------------------------------------------------
    // 辅助方法：为单品单期场景设置基础 mock
    // -------------------------------------------------------------------------
    private void setupSingleProductSinglePeriod(
            String itemCode, int period, double demand,
            double scrap, double safetyDays, double opDays, double inventory) {

        when(forecastRepository.findDistinctItemCodes()).thenReturn(List.of(itemCode));
        when(forecastRepository.findDistinctYearMonths()).thenReturn(List.of(period));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(itemCode, period))
                .thenReturn(Optional.of(new Forecast(null, itemCode, period, demand, null)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, opDays, opDays, 0.0, 0.0)));
        when(scrapRateRepository.findByItemCode(itemCode))
                .thenReturn(Optional.of(new ScrapRate(null, itemCode, scrap)));
        when(inventoryDaysRepository.findByItemCode(itemCode))
                .thenReturn(Optional.of(new InventoryDays(null, itemCode, safetyDays, null)));
        when(inventoryCountRepository.findFirstByItemCodeOrderByYearMonthDesc(itemCode))
                .thenReturn(Optional.of(new InventoryCount(null, itemCode, period, inventory, null)));
        when(bomRepository.findByParentCode(itemCode)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCode(itemCode)).thenReturn(Optional.empty());
    }

    // =========================================================================
    // 1. 基础计算公式
    // =========================================================================

    /**
     * 标准场景：需求100，稼动22天，安全7天，报废10%，期初库存0
     * planQty = (100/22*7 + 100 - 0) / 0.9 ≈ 146.46
     * isProduce = Y
     */
    @Test
    void testStandardFormula_withNoInitialInventory() {
        setupSingleProductSinglePeriod("P001", 202601, 100.0, 0.1, 7.0, 22.0, 0.0);

        service.calculate();

        verify(productionPlanRepository).save(planCaptor.capture());
        ProductionPlan plan = planCaptor.getValue();

        assertThat(plan.getItemCode()).isEqualTo("P001");
        assertThat(plan.getFinishedProductCode()).isEqualTo("P001");
        assertThat(plan.getYearMonth()).isEqualTo(202601);
        assertThat(plan.getForecast()).isEqualTo(100.0);
        assertThat(plan.getCurrentInventory()).isEqualTo(0.0);
        assertThat(plan.getIsProduce()).isEqualTo("Y");
        // (100/22*7 + 100) / 0.9 = 131.818.../0.9 ≈ 146.46
        assertThat(plan.getPlanQty()).isCloseTo(146.46, within(0.01));
    }

    /**
     * 库存充足：期初库存超过需求 → planQty 计算为负数，截断为 0，isProduce = N
     * demand=10, inventory=100 → planQty = (10/22*7 + 10 - 100)/1.0 < 0 → 0
     */
    @Test
    void testPlanQty_negativeClampsToZero_andIsProduce_N() {
        setupSingleProductSinglePeriod("P001", 202601, 10.0, 0.0, 7.0, 22.0, 100.0);

        service.calculate();

        verify(productionPlanRepository).save(planCaptor.capture());
        ProductionPlan plan = planCaptor.getValue();

        assertThat(plan.getPlanQty()).isEqualTo(0.0);
        assertThat(plan.getIsProduce()).isEqualTo("N");
    }

    /**
     * 需求 == 库存时 isProduce = N（非严格大于）
     */
    @Test
    void testIsProduce_N_whenDemandEqualsInventory() {
        setupSingleProductSinglePeriod("P001", 202601, 50.0, 0.0, 0.0, 22.0, 50.0);

        service.calculate();

        verify(productionPlanRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getIsProduce()).isEqualTo("N");
    }

    /**
     * 报废率 100% → oneMinusScrap == 0 → planQty 强制为 0（避免除零）
     */
    @Test
    void testScrapRateHundredPercent_planQtyForcedToZero() {
        setupSingleProductSinglePeriod("P001", 202601, 100.0, 1.0, 7.0, 22.0, 0.0);

        service.calculate();

        verify(productionPlanRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getPlanQty()).isEqualTo(0.0);
    }

    /**
     * 稼动天数为 0 → 安全库存项为 0，公式退化为 (需求 - 库存) / (1 - 报废率)
     * demand=100, scrap=0.1, inv=0 → planQty = 100 / 0.9 ≈ 111.11
     */
    @Test
    void testZeroOperatingDays_safetyTermSkipped() {
        setupSingleProductSinglePeriod("P001", 202601, 100.0, 0.1, 7.0, 0.0, 0.0);

        service.calculate();

        verify(productionPlanRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getPlanQty()).isCloseTo(111.11, within(0.01));
    }

    // =========================================================================
    // 2. 缺失配置数据 → 使用默认值 0
    // =========================================================================

    /**
     * 报废率/安全天数/稼动天数/期初库存均未配置 → 全默认为 0
     * planQty = (100 - 0) / 1.0 = 100.0
     */
    @Test
    void testMissingAllConfig_defaultsToZero() {
        String item = "P001";
        when(forecastRepository.findDistinctItemCodes()).thenReturn(List.of(item));
        when(forecastRepository.findDistinctYearMonths()).thenReturn(List.of(202601));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(item, 202601))
                .thenReturn(Optional.of(new Forecast(null, item, 202601, 100.0, null)));
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());
        when(scrapRateRepository.findByItemCode(item)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(item)).thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeOrderByYearMonthDesc(item))
                .thenReturn(Optional.empty());
        when(bomRepository.findByParentCode(item)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCode(item)).thenReturn(Optional.empty());

        service.calculate();

        verify(productionPlanRepository).save(planCaptor.capture());
        ProductionPlan plan = planCaptor.getValue();
        assertThat(plan.getPlanQty()).isEqualTo(100.0);
        assertThat(plan.getCurrentInventory()).isEqualTo(0.0);
        assertThat(plan.getScrapRate()).isEqualTo(0.0);
        assertThat(plan.getSafetyDays()).isEqualTo(0.0);
    }

    // =========================================================================
    // 3. 跨期库存结转
    // =========================================================================

    /**
     * 两个期间：第二期的 currentInventory 来自第一期结转
     *
     * 期1：demand=100, scrap=0.1, op=22, safe=7, inv=0
     *   planQty1 ≈ 146.46
     *
     * 期2：demand=120
     *   currentInventory = planQty1*(1-0.1) - 100 + 0 ≈ 31.82
     *   planQty2 = (120/22*7 + 120 - 31.82) / 0.9 ≈ 140.40
     */
    @Test
    void testMultiPeriod_inventoryCarryover() {
        String item = "P001";
        when(forecastRepository.findDistinctItemCodes()).thenReturn(List.of(item));
        when(forecastRepository.findDistinctYearMonths()).thenReturn(List.of(202601, 202602));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(item, 202601))
                .thenReturn(Optional.of(new Forecast(null, item, 202601, 100.0, null)));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(item, 202602))
                .thenReturn(Optional.of(new Forecast(null, item, 202602, 120.0, null)));
        when(operatingDaysRepository.findByYearMonth(anyInt()))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(scrapRateRepository.findByItemCode(item))
                .thenReturn(Optional.of(new ScrapRate(null, item, 0.1)));
        when(inventoryDaysRepository.findByItemCode(item))
                .thenReturn(Optional.of(new InventoryDays(null, item, 7.0, null)));
        when(inventoryCountRepository.findFirstByItemCodeOrderByYearMonthDesc(item))
                .thenReturn(Optional.of(new InventoryCount(null, item, 202601, 0.0, null)));
        when(bomRepository.findByParentCode(item)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCode(item)).thenReturn(Optional.empty());

        service.calculate();

        verify(productionPlanRepository, times(2)).save(planCaptor.capture());
        List<ProductionPlan> plans = planCaptor.getAllValues();
        ProductionPlan p1 = plans.get(0);
        ProductionPlan p2 = plans.get(1);

        assertThat(p1.getYearMonth()).isEqualTo(202601);
        assertThat(p1.getCurrentInventory()).isEqualTo(0.0);
        assertThat(p1.getPlanQty()).isCloseTo(146.46, within(0.01));

        assertThat(p2.getYearMonth()).isEqualTo(202602);
        // currentInventory = 146.46*(1-0.1) - 100 + 0 ≈ 31.82
        assertThat(p2.getCurrentInventory()).isCloseTo(31.82, within(0.01));
        // planQty = (120/22*7 + 120 - 31.82) / 0.9 ≈ 140.40
        assertThat(p2.getPlanQty()).isCloseTo(140.40, within(0.1));
        assertThat(p2.getIsProduce()).isEqualTo("Y");
    }

    /**
     * 某期间无预测 → 该期间跳过，不生成计划，也不更新跨期状态
     */
    @Test
    void testPeriodWithNoForecast_isSkipped() {
        String item = "P001";
        when(forecastRepository.findDistinctItemCodes()).thenReturn(List.of(item));
        when(forecastRepository.findDistinctYearMonths()).thenReturn(List.of(202601, 202602));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(item, 202601))
                .thenReturn(Optional.of(new Forecast(null, item, 202601, 100.0, null)));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(item, 202602))
                .thenReturn(Optional.empty()); // 202602 无预测
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(scrapRateRepository.findByItemCode(item)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(item)).thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeOrderByYearMonthDesc(item))
                .thenReturn(Optional.empty());
        when(bomRepository.findByParentCode(item)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCode(item)).thenReturn(Optional.empty());

        service.calculate();

        // 只有 202601 生成了计划
        verify(productionPlanRepository, times(1)).save(any(ProductionPlan.class));
    }

    // =========================================================================
    // 4. BOM 展开 & 需求传递
    // =========================================================================

    /**
     * 父件 P001 → 子件 C001（用量=2）
     * C001 的需求 = P001 的 planQty * 2
     */
    @Test
    void testBomChild_demandPropagatesFromParentPlanQty() {
        String parent = "P001";
        String child = "C001";
        int period = 202601;

        when(forecastRepository.findDistinctItemCodes()).thenReturn(List.of(parent));
        when(forecastRepository.findDistinctYearMonths()).thenReturn(List.of(period));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(parent, period))
                .thenReturn(Optional.of(new Forecast(null, parent, period, 100.0, null)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        // 父件参数
        when(scrapRateRepository.findByItemCode(parent))
                .thenReturn(Optional.of(new ScrapRate(null, parent, 0.1)));
        when(inventoryDaysRepository.findByItemCode(parent))
                .thenReturn(Optional.of(new InventoryDays(null, parent, 7.0, null)));
        when(inventoryCountRepository.findFirstByItemCodeOrderByYearMonthDesc(parent))
                .thenReturn(Optional.of(new InventoryCount(null, parent, period, 0.0, null)));
        when(bomRepository.findFirstByParentCode(parent)).thenReturn(Optional.empty());

        // 子件参数（报废率0、安全天数0）
        when(scrapRateRepository.findByItemCode(child)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(child)).thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeOrderByYearMonthDesc(child))
                .thenReturn(Optional.of(new InventoryCount(null, child, period, 0.0, null)));
        when(bomRepository.findByParentCode(child)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCode(child)).thenReturn(Optional.empty());

        // BOM 关系
        Bom bomRow = new Bom(null, parent, child, 2.0, null, null, null, null, null, null, null, null);
        when(bomRepository.findByParentCode(parent)).thenReturn(List.of(bomRow));

        service.calculate();

        verify(productionPlanRepository, times(2)).save(planCaptor.capture());
        List<ProductionPlan> plans = planCaptor.getAllValues();

        ProductionPlan parentPlan = plans.get(0);
        ProductionPlan childPlan = plans.get(1);

        assertThat(parentPlan.getItemCode()).isEqualTo(parent);
        assertThat(childPlan.getItemCode()).isEqualTo(child);

        double expectedParentQty = parentPlan.getPlanQty(); // ≈ 146.46
        assertThat(expectedParentQty).isCloseTo(146.46, within(0.01));

        // 子件需求 = 父件计划数量 * 2
        assertThat(childPlan.getForecast()).isCloseTo(expectedParentQty * 2, within(0.01));
        // 子件无报废无安全库存：planQty = demand / 1.0
        assertThat(childPlan.getPlanQty()).isCloseTo(expectedParentQty * 2, within(0.01));
    }

    /**
     * BOM 展开时子件包含工序/设备等工艺信息，应正确回填到结果
     */
    @Test
    void testBomNode_processAndEquipmentCopiedToResult() {
        String parent = "P001";
        String child = "C001";
        int period = 202601;

        when(forecastRepository.findDistinctItemCodes()).thenReturn(List.of(parent));
        when(forecastRepository.findDistinctYearMonths()).thenReturn(List.of(period));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(parent, period))
                .thenReturn(Optional.of(new Forecast(null, parent, period, 100.0, null)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        for (String code : List.of(parent, child)) {
            when(scrapRateRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryDaysRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryCountRepository.findFirstByItemCodeOrderByYearMonthDesc(code))
                    .thenReturn(Optional.empty());
            when(bomRepository.findByParentCode(code)).thenReturn(Collections.emptyList());
        }

        // C001 作为父零件的 BOM 行，含工序/设备信息
        Bom childBomInfo = new Bom(null, child, null, 0.0, "CNC", "EQ-01", 4, 30.0, 2.0, 15.0, null, null);
        when(bomRepository.findFirstByParentCode(child)).thenReturn(Optional.of(childBomInfo));
        when(bomRepository.findFirstByParentCode(parent)).thenReturn(Optional.empty());

        Bom bomRel = new Bom(null, parent, child, 1.0, null, null, null, null, null, null, null, null);
        when(bomRepository.findByParentCode(parent)).thenReturn(List.of(bomRel));

        service.calculate();

        verify(productionPlanRepository, times(2)).save(planCaptor.capture());
        ProductionPlan childPlan = planCaptor.getAllValues().get(1);

        assertThat(childPlan.getProcess()).isEqualTo("CNC");
        assertThat(childPlan.getEquipment()).isEqualTo("EQ-01");
        assertThat(childPlan.getMoldCavity()).isEqualTo(4);
        assertThat(childPlan.getCycleTime()).isEqualTo(30.0);
    }

    // =========================================================================
    // 5. 循环 BOM 保护
    // =========================================================================

    /**
     * P001 → C001 → P001（循环）：不应死循环，每个节点只计算一次
     */
    @Test
    void testCircularBom_doesNotInfiniteLoop() {
        String item1 = "P001";
        String item2 = "C001";
        int period = 202601;

        when(forecastRepository.findDistinctItemCodes()).thenReturn(List.of(item1));
        when(forecastRepository.findDistinctYearMonths()).thenReturn(List.of(period));
        when(forecastRepository.findFirstByItemCodeAndYearMonth(item1, period))
                .thenReturn(Optional.of(new Forecast(null, item1, period, 100.0, null)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        for (String code : List.of(item1, item2)) {
            when(scrapRateRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryDaysRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryCountRepository.findFirstByItemCodeOrderByYearMonthDesc(code))
                    .thenReturn(Optional.empty());
            when(bomRepository.findFirstByParentCode(code)).thenReturn(Optional.empty());
        }

        Bom bom1 = new Bom(null, item1, item2, 1.0, null, null, null, null, null, null, null, null);
        Bom bom2 = new Bom(null, item2, item1, 1.0, null, null, null, null, null, null, null, null);
        when(bomRepository.findByParentCode(item1)).thenReturn(List.of(bom1));
        when(bomRepository.findByParentCode(item2)).thenReturn(List.of(bom2));

        assertThatCode(() -> service.calculate()).doesNotThrowAnyException();
        // item1 和 item2 各计算一次
        verify(productionPlanRepository, times(2)).save(any(ProductionPlan.class));
    }

    // =========================================================================
    // 6. 计算前清空结果表
    // =========================================================================

    @Test
    void testCalculate_deletesAllPlansBefore() {
        when(forecastRepository.findDistinctItemCodes()).thenReturn(Collections.emptyList());
        when(forecastRepository.findDistinctYearMonths()).thenReturn(Collections.emptyList());

        service.calculate();

        verify(productionPlanRepository).deleteAllInBatch();
    }
}
