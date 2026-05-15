package com.aps.service;

import com.aps.dto.CalculateRequest;
import com.aps.entity.*;
import com.aps.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PlanCalculationService 单元测试
 *
 * 覆盖业务规则：
 *   完成品层：planQty = ceil(netDemand / (1 - scrapRate))
 *   半成品层：planQty = ceil((grossDemand - 库存 + 安全库存) / (1 - scrapRate))
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanCalculationServiceTest {

    @InjectMocks
    private PlanCalculationService service;

    @Mock private DemandRepository demandRepository;
    @Mock private ScrapRateRepository scrapRateRepository;
    @Mock private InventoryDaysRepository inventoryDaysRepository;
    @Mock private OperatingDaysRepository operatingDaysRepository;
    @Mock private SafetyStockRepository safetyStockRepository;
    @Mock private BomRepository bomRepository;
    @Mock private InventoryCountRepository inventoryCountRepository;
    @Mock private ProductionPlanRepository productionPlanRepository;

    private CalculateRequest request(String version) {
        CalculateRequest req = new CalculateRequest();
        req.setVersion(version);
        req.setResultVersion("result-" + version);
        return req;
    }

    // -------------------------------------------------------------------------
    // 辅助方法：为单品单期场景设置基础 mock
    // -------------------------------------------------------------------------
    private void setupSingleProductSinglePeriod(
            String itemCode, int period, double demand,
            double scrap, double safetyDays, double opDays, double inventory, String version) {

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(itemCode));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(itemCode, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", itemCode, period, demand, null, null, demand, null)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, opDays, opDays, 0.0, 0.0)));
        when(scrapRateRepository.findByItemCode(itemCode))
                .thenReturn(Optional.of(new ScrapRate(null, itemCode, scrap)));
        when(inventoryDaysRepository.findByItemCode(itemCode))
                .thenReturn(Optional.of(new InventoryDays(null, itemCode, safetyDays, null)));
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(itemCode, period, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(itemCode, version))
                .thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(itemCode, version))
                .thenReturn(Optional.of(new InventoryCount(null, itemCode, period, inventory, version)));
        when(bomRepository.findFirstByParentCodeAndVersion(itemCode, version)).thenReturn(Optional.empty());
        when(bomRepository.findByParentCodeAndVersion(itemCode, version)).thenReturn(Collections.emptyList());
    }

    @Test
    void calculate_withSingleVersionUsesVersionedRepositories() {
        String version = "20260331-1";
        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of("P001"));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(202601));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion("P001", 202601, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", "P001", 202601, 100.0, null, null, 100.0, version)));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(scrapRateRepository.findByItemCode("P001"))
                .thenReturn(Optional.of(new ScrapRate(null, "P001", 0.1)));
        when(inventoryDaysRepository.findByItemCode("P001"))
                .thenReturn(Optional.of(new InventoryDays(null, "P001", 7.0, null)));
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion("P001", 202601, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion("P001", version))
                .thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion("P001", version))
                .thenReturn(Optional.of(new InventoryCount(null, "P001", 202512, 0.0, version)));
        when(bomRepository.findFirstByParentCodeAndVersion("P001", version)).thenReturn(Optional.empty());
        when(bomRepository.findByParentCodeAndVersion("P001", version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        verify(demandRepository).findDistinctItemCodesByVersion(version);
        verify(demandRepository).findDistinctYearMonthsByVersion(version);
        verify(demandRepository, never()).findDistinctItemCodes();
        verify(demandRepository, never()).findDistinctYearMonths();
    }

    @Test
    void calculate_resultVersionMatchesBaseVersion() {
        String version = "20260331-1";
        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of("P001"));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(202601));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion("P001", 202601, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", "P001", 202601, 100.0, null, null, 100.0, version)));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(scrapRateRepository.findByItemCode("P001"))
                .thenReturn(Optional.of(new ScrapRate(null, "P001", 0.1)));
        when(inventoryDaysRepository.findByItemCode("P001"))
                .thenReturn(Optional.of(new InventoryDays(null, "P001", 7.0, null)));
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion("P001", 202601, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion("P001", version))
                .thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion("P001", version))
                .thenReturn(Optional.of(new InventoryCount(null, "P001", 202512, 0.0, version)));
        when(bomRepository.findFirstByParentCodeAndVersion("P001", version)).thenReturn(Optional.empty());
        when(bomRepository.findByParentCodeAndVersion("P001", version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).allMatch(p -> version.equals(p.getVersion()));
        verify(productionPlanRepository).deleteByVersion(version);
    }

    // =========================================================================
    // 1. 基础计算公式
    // =========================================================================

    /**
     * 标准场景：当前完成品无 BOM 报废率，直接等于需求
     */
    @Test
    void testStandardFormula_withNoInitialInventory() {
        String version = "202601";
        setupSingleProductSinglePeriod("P001", 202601, 100.0, 0.0, 7.0, 22.0, 0.0, version);

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository).saveAll(batchCaptor.capture());
        ProductionPlan plan = batchCaptor.getValue().get(0);

        assertThat(plan.getItemCode()).isEqualTo("P001");
        assertThat(plan.getFinishedProductCode()).isEqualTo("P001");
        assertThat(plan.getYearMonth()).isEqualTo(202601);
        assertThat(plan.getForecast()).isEqualTo(100.0);
        assertThat(plan.getCurrentInventory()).isEqualTo(0.0);
        assertThat(plan.getIsProduce()).isEqualTo("Y");
        assertThat(plan.getPlanQty()).isEqualTo(100.0);
    }

    /**
     * 当前完成品不考虑期初库存
     */
    @Test
    void testPlanQty_negativeClampsToZero_andIsProduce_N() {
        String version = "202601";
        setupSingleProductSinglePeriod("P001", 202601, 10.0, 0.0, 7.0, 22.0, 100.0, version);

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository).saveAll(batchCaptor.capture());
        ProductionPlan plan = batchCaptor.getValue().get(0);

        assertThat(plan.getPlanQty()).isEqualTo(10.0);
        assertThat(plan.getIsProduce()).isEqualTo("Y");
    }

    /**
     * 需求 == 库存时仍然生成计划
     */
    @Test
    void testIsProduce_N_whenDemandEqualsInventory() {
        String version = "202601";
        setupSingleProductSinglePeriod("P001", 202601, 50.0, 0.0, 0.0, 22.0, 50.0, version);

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getValue().get(0).getIsProduce()).isEqualTo("Y");
    }

    /**
     * 报废率 100% 需要通过 BOM 叶节点生效
     */
    @Test
    void testScrapRateHundredPercent_planQtyForcedToZero() {
        String version = "202601";
        setupSingleProductSinglePeriod("P001", 202601, 100.0, 0.0, 7.0, 22.0, 0.0, version);
        when(bomRepository.findFirstByParentCodeAndVersion("P001", version))
                .thenReturn(Optional.of(new Bom(null, "P001", null, 0.0, null, null, null, null, null, null, 1.0, version)));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getValue().get(0).getPlanQty()).isEqualTo(0.0);
    }

    /**
     * 当前实现不使用稼动天数，结果与标准场景一致
     */
    @Test
    void testZeroOperatingDays_safetyTermSkipped() {
        String version = "202601";
        setupSingleProductSinglePeriod("P001", 202601, 100.0, 0.0, 7.0, 0.0, 0.0, version);

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getValue().get(0).getPlanQty()).isEqualTo(100.0);
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
        String version = "202601";
        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(item));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(202601));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(item, 202601, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", item, 202601, 100.0, null, null, 100.0, version)));
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());
        when(scrapRateRepository.findByItemCode(item)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(item)).thenReturn(Optional.empty());
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(item, 202601, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(item, version)).thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(item, version))
                .thenReturn(Optional.empty());
        when(bomRepository.findByParentCodeAndVersion(item, version)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCodeAndVersion(item, version)).thenReturn(Optional.empty());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository).saveAll(batchCaptor.capture());
        ProductionPlan plan = batchCaptor.getValue().get(0);
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
     * 期1：demand=100, scrap=0.1, inv=0
     *   planQty1 = 100
     *
     * 期2：demand=120，结转库存影响当前库存
     */
    @Test
    void testMultiPeriod_inventoryCarryover() {
        String item = "P001";
        String version = "202601";
        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(item));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(202601, 202602));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(item, 202601, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", item, 202601, 100.0, null, null, 100.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(item, 202602, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", item, 202602, 120.0, null, null, 120.0, version)));
        when(operatingDaysRepository.findByYearMonth(anyInt()))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(scrapRateRepository.findByItemCode(item))
                .thenReturn(Optional.of(new ScrapRate(null, item, 0.1)));
        when(inventoryDaysRepository.findByItemCode(item))
                .thenReturn(Optional.of(new InventoryDays(null, item, 7.0, null)));
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(item, 202601, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(item, version))
                .thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(item, version))
                .thenReturn(Optional.of(new InventoryCount(null, item, 202601, 0.0, version)));
        when(bomRepository.findByParentCodeAndVersion(item, version)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCodeAndVersion(item, version)).thenReturn(Optional.empty());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository, times(2)).saveAll(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        ProductionPlan p1 = plans.get(0);
        ProductionPlan p2 = plans.get(1);

        assertThat(p1.getYearMonth()).isEqualTo(202601);
        assertThat(p1.getCurrentInventory()).isEqualTo(0.0);
        assertThat(p1.getPlanQty()).isEqualTo(100.0);

        assertThat(p2.getYearMonth()).isEqualTo(202602);
        assertThat(p2.getCurrentInventory()).isEqualTo(0.0);
        assertThat(p2.getPlanQty()).isEqualTo(120.0);
        assertThat(p2.getIsProduce()).isEqualTo("Y");
    }

    /**
     * 某期间无预测 → 该期间跳过，不生成计划，也不更新跨期状态
     */
    @Test
    void testPeriodWithNoForecast_isSkipped() {
        String item = "P001";
        String version = "202601";
        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(item));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(202601, 202602));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(item, 202601, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", item, 202601, 100.0, null, null, 100.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(item, 202602, version))
                .thenReturn(Optional.empty()); // 202602 无预测
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(scrapRateRepository.findByItemCode(item)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(item)).thenReturn(Optional.empty());
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(item, 202601, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(item, version)).thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(item, version))
                .thenReturn(Optional.empty());
        when(bomRepository.findByParentCodeAndVersion(item, version)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCodeAndVersion(item, version)).thenReturn(Optional.empty());

        service.calculate(request(version));

        // 只有 202601 生成了计划
        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository, times(2)).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getAllValues().stream().flatMap(List::stream).count()).isEqualTo(1);
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
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(parent));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", parent, period, 100.0, null, null, 100.0, version)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        // 父件参数
        when(scrapRateRepository.findByItemCode(parent))
                .thenReturn(Optional.of(new ScrapRate(null, parent, 0.1)));
        when(inventoryDaysRepository.findByItemCode(parent))
                .thenReturn(Optional.of(new InventoryDays(null, parent, 7.0, null)));
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(parent, period, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(parent, version))
                .thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(parent, version))
                .thenReturn(Optional.of(new InventoryCount(null, parent, period, 0.0, version)));
        when(bomRepository.findFirstByParentCodeAndVersion(parent, version)).thenReturn(Optional.empty());

        // 子件参数（报废率0、安全天数0）
        when(scrapRateRepository.findByItemCode(child)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(child)).thenReturn(Optional.empty());
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(child, period, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(child, version))
                .thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(child, version))
                .thenReturn(Optional.of(new InventoryCount(null, child, period, 0.0, version)));
        when(bomRepository.findByParentCodeAndVersion(child, version)).thenReturn(Collections.emptyList());
        when(bomRepository.findFirstByParentCodeAndVersion(child, version)).thenReturn(Optional.empty());

        // BOM 关系
        Bom bomRow = new Bom(null, parent, child, 2.0, null, null, null, null, null, null, null, version);
        when(bomRepository.findByParentCodeAndVersion(parent, version)).thenReturn(List.of(bomRow));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository, times(1)).saveAll(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        ProductionPlan parentPlan = plans.get(0);
        ProductionPlan childPlan = plans.get(1);

        assertThat(parentPlan.getItemCode()).isEqualTo(parent);
        assertThat(childPlan.getItemCode()).isEqualTo(child);

        double expectedParentQty = parentPlan.getPlanQty();
        assertThat(expectedParentQty).isEqualTo(100.0);

        assertThat(childPlan.getForecast()).isEqualTo(expectedParentQty * 2);
        assertThat(childPlan.getPlanQty()).isEqualTo(expectedParentQty * 2);
    }

    /**
     * BOM 展开时子件包含工序/设备等工艺信息，应正确回填到结果
     */
    @Test
    void testBomNode_processAndEquipmentCopiedToResult() {
        String parent = "P001";
        String child = "C001";
        int period = 202601;
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(parent));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", parent, period, 100.0, null, null, 100.0, version)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        for (String code : List.of(parent, child)) {
            when(scrapRateRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryDaysRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(code, period, version))
                    .thenReturn(Optional.empty());
            when(safetyStockRepository.findFirstByItemCodeAndVersion(code, version))
                    .thenReturn(Optional.empty());
            when(inventoryCountRepository.findFirstByItemCodeAndVersion(code, version))
                    .thenReturn(Optional.empty());
            when(bomRepository.findByParentCodeAndVersion(code, version)).thenReturn(Collections.emptyList());
        }

        // C001 作为父零件的 BOM 行，含工序/设备信息
        Bom childBomInfo = new Bom(null, child, null, 0.0, "CNC", "EQ-01", 4, 30.0, 2.0, 15.0, null, version);
        when(bomRepository.findFirstByParentCodeAndVersion(child, version)).thenReturn(Optional.of(childBomInfo));
        when(bomRepository.findFirstByParentCodeAndVersion(parent, version)).thenReturn(Optional.empty());

        Bom bomRel = new Bom(null, parent, child, 1.0, null, null, null, null, null, null, null, version);
        when(bomRepository.findByParentCodeAndVersion(parent, version)).thenReturn(List.of(bomRel));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository).saveAll(batchCaptor.capture());
        ProductionPlan childPlan = batchCaptor.getValue().get(1);

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
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(item1));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(item1, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", item1, period, 100.0, null, null, 100.0, version)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        for (String code : List.of(item1, item2)) {
            when(scrapRateRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryDaysRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(code, period, version))
                    .thenReturn(Optional.empty());
            when(safetyStockRepository.findFirstByItemCodeAndVersion(code, version))
                    .thenReturn(Optional.empty());
            when(inventoryCountRepository.findFirstByItemCodeAndVersion(code, version))
                    .thenReturn(Optional.empty());
            when(bomRepository.findFirstByParentCodeAndVersion(code, version)).thenReturn(Optional.empty());
        }

        Bom bom1 = new Bom(null, item1, item2, 1.0, null, null, null, null, null, null, null, version);
        Bom bom2 = new Bom(null, item2, item1, 1.0, null, null, null, null, null, null, null, version);
        when(bomRepository.findByParentCodeAndVersion(item1, version)).thenReturn(List.of(bom1));
        when(bomRepository.findByParentCodeAndVersion(item2, version)).thenReturn(List.of(bom2));

        assertThatCode(() -> service.calculate(request(version))).doesNotThrowAnyException();
        // item1 和 item2 各计算一次
        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanRepository, times(1)).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getAllValues().stream().flatMap(List::stream).count()).isEqualTo(2);
    }

    // =========================================================================
    // 6. 计算前清空结果表
    // =========================================================================

    @Test
    void testCalculate_deletesAllPlansBefore() {
        String version = "202601";
        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(Collections.emptyList());
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        verify(productionPlanRepository).deleteByVersion(version);
    }
}
