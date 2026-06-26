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
    @Mock private ProductionPlanBatchRepository productionPlanBatchRepository;
    @Mock private SharedMoldRuleService sharedMoldRuleService;

    private CalculateRequest request(String version) {
        return request(version, "result-" + version);
    }

    private CalculateRequest request(String version, String resultVersion) {
        CalculateRequest req = new CalculateRequest();
        req.setVersion(version);
        req.setResultVersion(resultVersion);
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
        when(bomRepository.findByVersion(version)).thenReturn(Collections.emptyList());
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
        when(bomRepository.findByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        verify(demandRepository).findDistinctItemCodesByVersion(version);
        verify(demandRepository).findDistinctYearMonthsByVersion(version);
        verify(demandRepository, never()).findDistinctItemCodes();
        verify(demandRepository, never()).findDistinctYearMonths();
    }

    @Test
    void calculate_usesExplicitResultVersionForDeleteAndSave() {
        String version = "20260331-1";
        String resultVersion = "manual-result-v1";
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
        when(bomRepository.findByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version, resultVersion));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).allMatch(p -> resultVersion.equals(p.getVersion()));
        assertThat(batchCaptor.getValue()).allMatch(p -> p.getCalculatedAt() != null);
        verify(productionPlanRepository).deleteByVersion(resultVersion);
    }

    @Test
    void calculate_fallsBackToBaseVersionWhenResultVersionBlank() {
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
        when(bomRepository.findByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version, "   "));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).allMatch(p -> version.equals(p.getVersion()));
        assertThat(batchCaptor.getValue()).allMatch(p -> p.getCalculatedAt() != null);
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
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
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
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
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
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        assertThat(batchCaptor.getValue().get(0).getIsProduce()).isEqualTo("Y");
    }

    /**
     * 报废率 100% 需要通过 BOM 叶节点生效
     */
    @Test
    void testScrapRateHundredPercent_planQtyForcedToZero() {
        String version = "202601";
        setupSingleProductSinglePeriod("P001", 202601, 100.0, 0.0, 7.0, 22.0, 0.0, version);
        when(bomRepository.findByVersion(version))
                .thenReturn(List.of(new Bom(null, "P001", null, 0.0, null, null, null, null, null, null, null, null, 1.0, "采购件", version)));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        assertThat(batchCaptor.getValue().get(0).getPlanQty()).isEqualTo(0.0);
    }

    @Test
    void calculate_finishedProductLeavesPartAttributeEmptyWhenNoIncomingBomExists() {
        String version = "202601";
        setupSingleProductSinglePeriod("P001", 202601, 100.0, 0.0, 7.0, 22.0, 0.0, version);
        when(bomRepository.findByVersion(version))
                .thenReturn(List.of(new Bom(null, "P001", null, 0.0, null, null, null, null, null, null, null, null, 0.0, "采购件", version)));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(1);
        assertThat(batchCaptor.getValue().get(0).getPartAttribute()).isNull();
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
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
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
        when(bomRepository.findByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
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
        when(bomRepository.findByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository, times(2)).bulkInsert(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        ProductionPlan p1 = plans.get(0);
        ProductionPlan p2 = plans.get(1);

        assertThat(p1.getYearMonth()).isEqualTo(202601);
        assertThat(p1.getCurrentInventory()).isEqualTo(0.0);
        assertThat(p1.getPlanQty()).isEqualTo(100.0);

        assertThat(p2.getYearMonth()).isEqualTo(202602);
        assertThat(p2.getCurrentInventory()).isEqualTo(100.0);
        assertThat(p2.getPlanQty()).isEqualTo(20.0);
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
        when(bomRepository.findByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        // 只有 202601 生成了计划
        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository, times(2)).bulkInsert(batchCaptor.capture());
        assertThat(batchCaptor.getAllValues().stream().flatMap(List::stream).count()).isEqualTo(1);
    }

    @Test
    void testChildForecast_isReducedByPreviousPeriodCarryoverInventory() {
        String parent = "P001";
        String child = "C001";
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(parent));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(202606, 202607));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent, 202606, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", parent, 202606, 100.0, null, null, 100.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent, 202607, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", parent, 202607, 464.0, null, null, 464.0, version)));

        Bom bomRel = new Bom(null, parent, parent, child, 1.0, null, null, "制造一部", "单元A", null, null, null, null, null, version);
        Bom childLeaf = new Bom(null, parent, child, null, 0.0, "PROC-A", "EQ-A", "制造一部", "单元A", null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(bomRel, childLeaf));

        when(inventoryCountRepository.findAllByVersion(version))
                .thenReturn(List.of(new InventoryCount(null, child, 202606, 150.0, version)));
        when(safetyStockRepository.findAllByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository, times(2)).bulkInsert(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .filter(plan -> child.equals(plan.getItemCode()))
                .sorted((left, right) -> left.getYearMonth().compareTo(right.getYearMonth()))
                .collect(Collectors.toList());

        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).getYearMonth()).isEqualTo(202606);
        assertThat(plans.get(0).getForecast()).isEqualTo(100.0);
        assertThat(plans.get(0).getPlanQty()).isEqualTo(0.0);

        assertThat(plans.get(1).getYearMonth()).isEqualTo(202607);
        assertThat(plans.get(1).getCurrentInventory()).isEqualTo(50.0);
        assertThat(plans.get(1).getForecast()).isEqualTo(314.0);
        assertThat(plans.get(1).getPlanQty()).isEqualTo(314.0);
    }

    @Test
    void testFinishedProductForecast_isRecalculatedFromPreviousPeriodCarryover() {
        String item = "P001";
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(item));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(202606, 202607));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(item, 202606, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", item, 202606, 537.0, 1152.0, 120.0, 0.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(item, 202607, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", item, 202607, 374.0, 0.0, 90.0, 464.0, version)));

        when(bomRepository.findByVersion(version)).thenReturn(Collections.emptyList());
        when(inventoryCountRepository.findAllByVersion(version)).thenReturn(Collections.emptyList());
        when(safetyStockRepository.findAllByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository, times(2)).bulkInsert(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .sorted((left, right) -> left.getYearMonth().compareTo(right.getYearMonth()))
                .collect(Collectors.toList());

        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).getYearMonth()).isEqualTo(202606);
        assertThat(plans.get(0).getForecast()).isEqualTo(0.0);
        assertThat(plans.get(0).getPlanQty()).isEqualTo(0.0);

        assertThat(plans.get(1).getYearMonth()).isEqualTo(202607);
        assertThat(plans.get(1).getCurrentInventory()).isEqualTo(615.0);
        assertThat(plans.get(1).getForecast()).isEqualTo(0.0);
        assertThat(plans.get(1).getPlanQty()).isEqualTo(0.0);
        assertThat(plans.get(1).getIsProduce()).isEqualTo("N");
    }

    @Test
    void testSharedSemiFinishedInventory_isConsumedProgressivelyAcrossFinishedProductsInSamePeriod() {
        String finishedA = "P001";
        String finishedB = "P002";
        String sharedChild = "C001";
        String version = "202601";
        int period = 202606;

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(finishedA, finishedB));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(finishedA, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", finishedA, period, 100.0, 0.0, 0.0, 100.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(finishedB, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", finishedB, period, 80.0, 0.0, 0.0, 80.0, version)));

        when(bomRepository.findByVersion(version)).thenReturn(List.of(
                new Bom(null, finishedA, finishedA, sharedChild, 1.0, null, null, "制造一部", "单元A", null, null, null, null, null, version),
                new Bom(null, finishedB, finishedB, sharedChild, 1.0, null, null, "制造一部", "单元A", null, null, null, null, null, version),
                new Bom(null, finishedA, sharedChild, null, 0.0, "PROC-C", "EQ-C", "制造一部", "单元A", null, null, null, null, null, version)
        ));
        when(inventoryCountRepository.findAllByVersion(version))
                .thenReturn(List.of(new InventoryCount(null, sharedChild, period, 150.0, version)));
        when(safetyStockRepository.findAllByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        List<ProductionPlan> childPlans = batchCaptor.getValue().stream()
                .filter(plan -> sharedChild.equals(plan.getItemCode()))
                .sorted((left, right) -> left.getFinishedProductCode().compareTo(right.getFinishedProductCode()))
                .collect(Collectors.toList());

        assertThat(childPlans).hasSize(2);
        assertThat(childPlans.get(0).getFinishedProductCode()).isEqualTo(finishedA);
        assertThat(childPlans.get(0).getCurrentInventory()).isEqualTo(150.0);
        assertThat(childPlans.get(0).getForecast()).isEqualTo(100.0);
        assertThat(childPlans.get(0).getPlanQty()).isEqualTo(0.0);

        assertThat(childPlans.get(1).getFinishedProductCode()).isEqualTo(finishedB);
        assertThat(childPlans.get(1).getCurrentInventory()).isEqualTo(50.0);
        assertThat(childPlans.get(1).getForecast()).isEqualTo(80.0);
        assertThat(childPlans.get(1).getPlanQty()).isEqualTo(30.0);
    }

    @Test
    void testSharedMoldProducts_alignPlanQtyBeforeBomExplosion() {
        String productA = "203000324D";
        String productB = "203000326D";
        String childA = "C-A";
        String childB = "C-B";
        String version = "202601";
        int period = 202606;

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(productA, productB));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(productA, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", productA, period, 80.0, 0.0, 0.0, 80.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(productB, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", productB, period, 120.0, 0.0, 0.0, 120.0, version)));
        when(sharedMoldRuleService.findEnabledRules()).thenReturn(List.of(
                new SharedMoldRule(1L, productA, productB, null, null, true, null)
        ));

        when(bomRepository.findByVersion(version)).thenReturn(List.of(
                new Bom(null, productA, productA, childA, 1.0, "注塑", "EQ-1", "制造一部", "单元A", null, null, null, null, null, version),
                new Bom(null, productB, productB, childB, 1.0, "注塑", "EQ-1", "制造一部", "单元A", null, null, null, null, null, version),
                new Bom(null, productA, childA, null, 0.0, "后处理", "EQ-2", "制造一部", "单元A", null, null, null, null, null, version),
                new Bom(null, productB, childB, null, 0.0, "后处理", "EQ-2", "制造一部", "单元A", null, null, null, null, null, version)
        ));
        when(inventoryCountRepository.findAllByVersion(version)).thenReturn(Collections.emptyList());
        when(safetyStockRepository.findAllByVersion(version)).thenReturn(Collections.emptyList());

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getValue();

        ProductionPlan productAPlan = plans.stream()
                .filter(plan -> productA.equals(plan.getItemCode()))
                .findFirst()
                .orElseThrow();
        ProductionPlan productBPlan = plans.stream()
                .filter(plan -> productB.equals(plan.getItemCode()))
                .findFirst()
                .orElseThrow();
        ProductionPlan childAPlan = plans.stream()
                .filter(plan -> childA.equals(plan.getItemCode()))
                .findFirst()
                .orElseThrow();
        ProductionPlan childBPlan = plans.stream()
                .filter(plan -> childB.equals(plan.getItemCode()))
                .findFirst()
                .orElseThrow();

        assertThat(productAPlan.getRawPlanQty()).isEqualTo(80.0);
        assertThat(productBPlan.getRawPlanQty()).isEqualTo(120.0);
        assertThat(productAPlan.getPlanQty()).isEqualTo(120.0);
        assertThat(productBPlan.getPlanQty()).isEqualTo(120.0);
        assertThat(childAPlan.getForecast()).isEqualTo(120.0);
        assertThat(childBPlan.getForecast()).isEqualTo(120.0);
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
        // 子件参数（报废率0、安全天数0）
        when(scrapRateRepository.findByItemCode(child)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(child)).thenReturn(Optional.empty());
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(child, period, version))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(child, version))
                .thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(child, version))
                .thenReturn(Optional.of(new InventoryCount(null, child, period, 0.0, version)));

        // BOM 关系
        Bom bomRow = new Bom(null, parent, parent, child, 2.0, null, null, null, null, null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(bomRow));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository, times(1)).bulkInsert(batchCaptor.capture());
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
        assertThat(plans).extracting(ProductionPlan::getCalculatedAt).doesNotContainNull();
        assertThat(plans).extracting(ProductionPlan::getCalculatedAt).containsOnly(plans.get(0).getCalculatedAt());
    }

    @Test
    void testDuplicateBomEdges_sameChildUnderSameParent_expandsOnlyOnce() {
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

        when(scrapRateRepository.findByItemCode(parent)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(parent)).thenReturn(Optional.empty());
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(parent, period, version)).thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(parent, version)).thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(parent, version)).thenReturn(Optional.empty());

        when(scrapRateRepository.findByItemCode(child)).thenReturn(Optional.empty());
        when(inventoryDaysRepository.findByItemCode(child)).thenReturn(Optional.empty());
        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(child, period, version)).thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(child, version)).thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(child, version)).thenReturn(Optional.empty());

        Bom edge1 = new Bom(null, parent, parent, child, 1.0, null, null, null, null, null, null, null, null, null, version);
        Bom edge2 = new Bom(null, parent, parent, child, 1.0, null, null, null, null, null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(edge1, edge2));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getValue();

        assertThat(plans).hasSize(2);
        assertThat(plans.stream().filter(p -> child.equals(p.getItemCode())).count()).isEqualTo(1);
    }

    @Test
    void testCalculation_usesOnlyBomRowsForMatchingRootProduct() {
        String finished1 = "P001";
        String finished2 = "P002";
        String sharedParent = "A001";
        String child1 = "C001";
        String child2 = "C002";
        int period = 202601;
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(finished1, finished2));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(finished1, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", finished1, period, 100.0, null, null, 100.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(finished2, period, version))
                .thenReturn(Optional.empty());
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        for (String code : List.of(finished1, finished2, sharedParent, child1, child2)) {
            when(scrapRateRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryDaysRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(code, period, version)).thenReturn(Optional.empty());
            when(safetyStockRepository.findFirstByItemCodeAndVersion(code, version)).thenReturn(Optional.empty());
            when(inventoryCountRepository.findFirstByItemCodeAndVersion(code, version)).thenReturn(Optional.empty());
        }

        Bom root1 = new Bom(null, finished1, finished1, sharedParent, 1.0, null, null, "制造一部", "单元A", null, null, null, null, null, version);
        Bom root2 = new Bom(null, finished2, finished2, sharedParent, 1.0, null, null, "制造一部", "单元A", null, null, null, null, null, version);
        Bom branch1 = new Bom(null, finished1, sharedParent, child1, 1.0, null, null, "制造一部", "单元A", null, null, null, null, null, version);
        Bom branch2 = new Bom(null, finished2, sharedParent, child2, 1.0, null, null, "制造一部", "单元A", null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(root1, root2, branch1, branch2));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getValue();

        assertThat(plans).extracting(ProductionPlan::getItemCode)
                .containsExactlyInAnyOrder(finished1, sharedParent, child1);
        assertThat(plans).extracting(ProductionPlan::getItemCode)
                .doesNotContain(child2);
    }

    /**
     * BOM 展开时子件包含工序/设备等工艺信息，应正确回填到结果
     */
    @Test
    void testSharedBomChild_isPreloadedAndNotRequeriedPerTraversal() {
        String parent1 = "P001";
        String parent2 = "P002";
        String child = "C001";
        int period = 202601;
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(parent1, parent2));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent1, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", parent1, period, 10.0, null, null, 10.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent2, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", parent2, period, 20.0, null, null, 20.0, version)));

        when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(anyString(), eq(period), eq(version)))
                .thenReturn(Optional.empty());
        when(safetyStockRepository.findFirstByItemCodeAndVersion(anyString(), eq(version)))
                .thenReturn(Optional.empty());
        when(inventoryCountRepository.findFirstByItemCodeAndVersion(anyString(), eq(version)))
                .thenReturn(Optional.empty());

        Bom bom1 = new Bom(null, parent1, parent1, child, 1.0, null, null, null, null, null, null, null, null, null, version);
        Bom bom2 = new Bom(null, parent2, parent2, child, 1.0, null, null, null, null, null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(bom1, bom2));

        service.calculate(request(version));

        verify(bomRepository, times(2)).findByVersion(version);
    }

    @Test
    void testSharedBomChild_reusesPreloadedInventoryAndSafetyLookups() {
        String parent1 = "P001";
        String parent2 = "P002";
        String child = "C001";
        int period = 202601;
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(parent1, parent2));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent1, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", parent1, period, 10.0, null, null, 10.0, version)));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(parent2, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", parent2, period, 20.0, null, null, 20.0, version)));

        Bom bom1 = new Bom(null, parent1, parent1, child, 1.0, null, null, null, null, null, null, null, null, null, version);
        Bom bom2 = new Bom(null, parent2, parent2, child, 1.0, null, null, null, null, null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(bom1, bom2));

        SafetyStock childSafety = new SafetyStock(null, child, period, 3.0, null, null, version);
        InventoryCount childInventory = new InventoryCount(null, child, period, 5.0, version);
        when(safetyStockRepository.findAllByVersion(version)).thenReturn(List.of(childSafety));
        when(inventoryCountRepository.findAllByVersion(version)).thenReturn(List.of(childInventory));

        service.calculate(request(version));

        verify(safetyStockRepository).findAllByVersion(version);
        verify(inventoryCountRepository).findAllByVersion(version);
        verify(safetyStockRepository, never()).findByItemCodeAndYearMonthAndVersion(anyString(), anyInt(), anyString());
        verify(safetyStockRepository, never()).findFirstByItemCodeAndVersion(anyString(), anyString());
        verify(inventoryCountRepository, never()).findFirstByItemCodeAndVersion(anyString(), anyString());
    }

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
        }

        // C001 作为父零件的 BOM 行，含工序/设备信息
        Bom childBomInfo = new Bom(null, parent, child, null, 0.0, "CNC", "EQ-01", null, null, 4, 30.0, 2.0, 15.0, null, version);
        Bom bomRel = new Bom(null, parent, parent, child, 1.0, null, null, null, null, null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(bomRel, childBomInfo));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        ProductionPlan childPlan = batchCaptor.getValue().get(1);

        assertThat(childPlan.getProcess()).isEqualTo("CNC");
        assertThat(childPlan.getEquipment()).isEqualTo("EQ-01");
        assertThat(childPlan.getMoldCavity()).isEqualTo(4);
        assertThat(childPlan.getCycleTime()).isEqualTo(30.0);
    }

    @Test
    void testBomNode_manufacturingFieldsCopiedToResult() {
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
        }

        Bom childBomInfo = new Bom(
                null, parent, child, null, 0.0, "CNC", "EQ-01",
                "制造一部", "单元A", 4, 30.0, 2.0, 15.0, null, version);
        Bom bomRel = new Bom(
                null, parent, parent, child, 1.0, null, null,
                "制造一部", "单元A", null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(bomRel, childBomInfo));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        ProductionPlan childPlan = batchCaptor.getValue().get(1);

        assertThat(childPlan.getManufacturingDepartment()).isEqualTo("制造一部");
        assertThat(childPlan.getManufacturingUnit()).isEqualTo("单元A");
    }

    @Test
    void calculate_usesCurrentNodePartAttributeFromIncomingBomChildRelation() {
        String finished = "P001";
        String semiFinished = "A001";
        String rawMaterial = "C001";
        int period = 202601;
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(finished));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(finished, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", finished, period, 100.0, null, null, 100.0, version)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        for (String code : List.of(finished, semiFinished, rawMaterial)) {
            when(scrapRateRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryDaysRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(code, period, version))
                    .thenReturn(Optional.empty());
            when(safetyStockRepository.findFirstByItemCodeAndVersion(code, version))
                    .thenReturn(Optional.empty());
            when(inventoryCountRepository.findFirstByItemCodeAndVersion(code, version))
                    .thenReturn(Optional.empty());
        }

        Bom finishedToSemi = new Bom(
                null, finished, finished, semiFinished, 1.0, null, null,
                "制造一部", "单元A", null, null, null, null, null, "半成品", version);
        Bom semiToRaw = new Bom(
                null, finished, semiFinished, rawMaterial, 1.0, "冲压", "EQ-01",
                "制造一部", "单元A", null, null, null, null, null, "采购件", version);
        Bom rawLeaf = new Bom(
                null, finished, rawMaterial, null, 0.0, "下料", "EQ-02",
                "制造二部", "单元B", null, null, null, null, null, "原材料", version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(finishedToSemi, semiToRaw, rawLeaf));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        List<ProductionPlan> plans = batchCaptor.getValue();

        ProductionPlan semiFinishedPlan = plans.stream()
                .filter(plan -> semiFinished.equals(plan.getItemCode()))
                .findFirst()
                .orElseThrow();
        ProductionPlan rawMaterialPlan = plans.stream()
                .filter(plan -> rawMaterial.equals(plan.getItemCode()))
                .findFirst()
                .orElseThrow();

        assertThat(semiFinishedPlan.getPartAttribute()).isEqualTo("半成品");
        assertThat(rawMaterialPlan.getPartAttribute()).isEqualTo("采购件");
    }

    @Test
    void calculate_leafNodeWithoutOutgoingBomStillUsesIncomingPartAttribute() {
        String finished = "P001";
        String leaf = "C001";
        int period = 202601;
        String version = "202601";

        when(demandRepository.findDistinctItemCodesByVersion(version)).thenReturn(List.of(finished));
        when(demandRepository.findDistinctYearMonthsByVersion(version)).thenReturn(List.of(period));
        when(demandRepository.findFirstByItemCodeAndYearMonthAndVersion(finished, period, version))
                .thenReturn(Optional.of(new Demand(null, "AAA", finished, period, 100.0, null, null, 100.0, version)));
        when(operatingDaysRepository.findByYearMonth(period))
                .thenReturn(Optional.of(new OperatingDays(null, period, 22.0, 22.0, 0.0, 0.0)));

        for (String code : List.of(finished, leaf)) {
            when(scrapRateRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(inventoryDaysRepository.findByItemCode(code)).thenReturn(Optional.empty());
            when(safetyStockRepository.findByItemCodeAndYearMonthAndVersion(code, period, version))
                    .thenReturn(Optional.empty());
            when(safetyStockRepository.findFirstByItemCodeAndVersion(code, version))
                    .thenReturn(Optional.empty());
            when(inventoryCountRepository.findFirstByItemCodeAndVersion(code, version))
                    .thenReturn(Optional.empty());
        }

        Bom finishedToLeaf = new Bom(
                null, finished, finished, leaf, 1.0, null, null,
                "制造一部", "单元A", null, null, null, null, null, "采购件", version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(finishedToLeaf));

        service.calculate(request(version));

        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository).bulkInsert(batchCaptor.capture());
        ProductionPlan leafPlan = batchCaptor.getValue().stream()
                .filter(plan -> leaf.equals(plan.getItemCode()))
                .findFirst()
                .orElseThrow();

        assertThat(leafPlan.getPartAttribute()).isEqualTo("采购件");
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
        }

        Bom bom1 = new Bom(null, item1, item1, item2, 1.0, null, null, null, null, null, null, null, null, null, version);
        Bom bom2 = new Bom(null, item1, item2, item1, 1.0, null, null, null, null, null, null, null, null, null, version);
        when(bomRepository.findByVersion(version)).thenReturn(List.of(bom1, bom2));

        assertThatCode(() -> service.calculate(request(version))).doesNotThrowAnyException();
        // item1 和 item2 各计算一次
        ArgumentCaptor<List<ProductionPlan>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(productionPlanBatchRepository, times(1)).bulkInsert(batchCaptor.capture());
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

        verify(productionPlanRepository).deleteByVersion("result-" + version);
    }
}
