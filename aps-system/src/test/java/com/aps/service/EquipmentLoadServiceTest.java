package com.aps.service;

import com.aps.entity.OperatingDays;
import com.aps.entity.ProductionPlan;
import com.aps.repository.OperatingDaysRepository;
import com.aps.repository.ProductionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EquipmentLoadService 单元测试
 *
 * 业务规则（FUNC-EL-003/004）：
 *   taskTimeHours    = sum(planQty × taktTime / 3600)，按 (equipment, yearMonth) 聚合
 *   availableTimeHours = operatingDays × DEFAULT_HOURS_PER_DAY (10.5)
 *   utilizationRate  = taskTimeHours / availableTimeHours
 *   status: LOOSE(<50%), NORMAL(50-85%), TIGHT(85-100%), OVERLOADED(>=100%)
 */
@ExtendWith(MockitoExtension.class)
class EquipmentLoadServiceTest {

    @InjectMocks
    private EquipmentLoadService service;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private OperatingDaysRepository operatingDaysRepository;

    private ProductionPlan makePlan(String equipment, String process, Integer yearMonth,
                                    Double planQty, Double taktTime) {
        ProductionPlan p = new ProductionPlan();
        p.setEquipment(equipment);
        p.setProcess(process);
        p.setYearMonth(yearMonth);
        p.setPlanQty(planQty);
        p.setTaktTime(taktTime);
        return p;
    }

    // =========================================================================
    // 1. 任务时间计算
    // =========================================================================

    /**
     * planQty=3600, taktTime=1s → taskTimeHours = 3600*1/3600 = 1.0h
     * operatingDays=22, hoursPerDay=10.5 → availableTime=231h
     * utilizationRate ≈ 0.433%
     */
    @Test
    void singlePlan_taskTimeHoursCalculatedCorrectly() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("EQ-01", "CNC", 202601, 3600.0, 1.0)));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        EquipmentLoadRow row = result.get(0);
        assertThat(row.getEquipment()).isEqualTo("EQ-01");
        assertThat(row.getProcess()).isEqualTo("CNC");
        assertThat(row.getYearMonth()).isEqualTo(202601);
        assertThat(row.getTaskTimeHours()).isCloseTo(1.0, within(0.001));
        assertThat(row.getAvailableTimeHours()).isCloseTo(231.0, within(0.001));  // 22 * 10.5
        assertThat(row.getUtilizationRate()).isCloseTo(1.0 / 231.0, within(0.0001));
    }

    /**
     * 同一设备+期间的多条计划，taskTime 累加
     */
    @Test
    void multiplePlansForSameEquipmentAndPeriod_sumsTaskTime() {
        List<ProductionPlan> plans = List.of(
                makePlan("EQ-01", "CNC", 202601, 1800.0, 2.0),  // 1800*2/3600 = 1.0h
                makePlan("EQ-01", "CNC", 202601, 3600.0, 1.0)   // 3600*1/3600 = 1.0h
        );
        when(productionPlanRepository.findAll()).thenReturn(plans);
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTaskTimeHours()).isCloseTo(2.0, within(0.001));
    }

    /**
     * 不同设备产生不同行
     */
    @Test
    void differentEquipments_producesSeparateRows() {
        List<ProductionPlan> plans = List.of(
                makePlan("EQ-01", "CNC", 202601, 3600.0, 1.0),
                makePlan("EQ-02", "WELD", 202601, 3600.0, 2.0)
        );
        when(productionPlanRepository.findAll()).thenReturn(plans);
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(2);
    }

    // =========================================================================
    // 2. 利用率状态判断
    // =========================================================================

    /**
     * 利用率 < 50% → LOOSE
     */
    @Test
    void utilizationBelow50_statusIsLoose() {
        // taskTime = 100/3600 h ≈ 0.0278h, availableTime = 22*10.5 = 231h → rate ≈ 0.012%
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("EQ-01", "CNC", 202601, 100.0, 1.0)));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result.get(0).getStatus()).isEqualTo("LOOSE");
    }

    /**
     * 50% ≤ 利用率 < 85% → NORMAL
     * taskTime = 231*0.6 = 138.6h → planQty*taktTime/3600 = 138.6 → planQty=498960, takt=1s
     */
    @Test
    void utilizationBetween50And85_statusIsNormal() {
        double available = 22.0 * 10.5;          // 231h
        double taskTime = available * 0.7;        // 161.7h (70%)
        double planQty = taskTime * 3600;         // taktTime=1s
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("EQ-01", "CNC", 202601, planQty, 1.0)));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result.get(0).getStatus()).isEqualTo("NORMAL");
    }

    /**
     * 85% ≤ 利用率 < 100% → TIGHT
     */
    @Test
    void utilizationBetween85And100_statusIsTight() {
        double available = 22.0 * 10.5;
        double taskTime = available * 0.9;        // 90%
        double planQty = taskTime * 3600;
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("EQ-01", "CNC", 202601, planQty, 1.0)));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result.get(0).getStatus()).isEqualTo("TIGHT");
    }

    /**
     * 利用率 ≥ 100% → OVERLOADED
     */
    @Test
    void utilizationAtOrAbove100_statusIsOverloaded() {
        double available = 22.0 * 10.5;
        double planQty = available * 3600;        // 恰好 100%（taktTime=1s）
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("EQ-01", "CNC", 202601, planQty, 1.0)));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result.get(0).getStatus()).isEqualTo("OVERLOADED");
    }

    // =========================================================================
    // 3. 过滤逻辑
    // =========================================================================

    /**
     * equipment 为 null → 跳过
     */
    @Test
    void planWithNullEquipment_isExcluded() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan(null, "CNC", 202601, 3600.0, 1.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).isEmpty();
    }

    /**
     * taktTime 为 null → 跳过
     */
    @Test
    void planWithNullTaktTime_isExcluded() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("EQ-01", "CNC", 202601, 3600.0, null)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).isEmpty();
    }

    /**
     * planQty 为 null → 跳过
     */
    @Test
    void planWithNullPlanQty_isExcluded() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("EQ-01", "CNC", 202601, null, 1.0)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).isEmpty();
    }

    /**
     * 无稼动天数配置 → availableTimeHours=0，utilizationRate=0，status=LOOSE
     */
    @Test
    void noOperatingDays_availableTimeIsZero_statusIsLoose() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("EQ-01", "CNC", 202601, 3600.0, 1.0)));
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAvailableTimeHours()).isEqualTo(0.0);
        assertThat(result.get(0).getUtilizationRate()).isEqualTo(0.0);
        assertThat(result.get(0).getStatus()).isEqualTo("LOOSE");
    }

    // =========================================================================
    // 4. 期间过滤
    // =========================================================================

    @Test
    void withPeriodFilter_queriesByYearMonthIn() {
        List<Integer> periods = List.of(202601);
        when(productionPlanRepository.findByYearMonthIn(periods)).thenReturn(Collections.emptyList());

        service.calculateEquipmentLoad(periods);

        verify(productionPlanRepository).findByYearMonthIn(periods);
        verify(productionPlanRepository, never()).findAll();
    }

    // =========================================================================
    // 5. 空结果
    // =========================================================================

    @Test
    void noPlans_returnsEmptyList() {
        when(productionPlanRepository.findAll()).thenReturn(Collections.emptyList());

        assertThat(service.calculateEquipmentLoad(null)).isEmpty();
    }
}
