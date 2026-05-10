package com.aps.service;

import com.aps.entity.ProductionPlan;
import com.aps.repository.ProductionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WorkforceReportService 单元测试
 *
 * 业务规则：
 *   一线人员需求 = planQty × staffCount（持台人数）
 *   按 (process, equipment, yearMonth) 聚合求和
 *   staffCount 为 null 或 0 的记录不参与计算
 */
@ExtendWith(MockitoExtension.class)
class WorkforceReportServiceTest {

    @InjectMocks
    private WorkforceReportService service;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    private ProductionPlan makePlan(String process, String equipment, Integer yearMonth,
                                    Double planQty, Double staffCount) {
        ProductionPlan p = new ProductionPlan();
        p.setProcess(process);
        p.setEquipment(equipment);
        p.setYearMonth(yearMonth);
        p.setPlanQty(planQty);
        p.setStaffCount(staffCount);
        return p;
    }

    // =========================================================================
    // 1. 基础计算
    // =========================================================================

    /**
     * 单条计划：planQty=100, staffCount=2 → workforceDemand=200
     */
    @Test
    void singlePlan_workforceDemandIsQtyTimesStaff() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("CNC", "EQ-01", 202601, 100.0, 2.0)));

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).hasSize(1);
        WorkforceRow row = result.get(0);
        assertThat(row.getProcess()).isEqualTo("CNC");
        assertThat(row.getEquipment()).isEqualTo("EQ-01");
        assertThat(row.getYearMonth()).isEqualTo(202601);
        assertThat(row.getWorkforceDemand()).isCloseTo(200.0, within(0.001));
    }

    /**
     * 同一工序+设备+期间的多条计划累加
     */
    @Test
    void multiplePlansForSameKey_sumWorkforceDemand() {
        List<ProductionPlan> plans = List.of(
                makePlan("CNC", "EQ-01", 202601, 100.0, 2.0),  // 200
                makePlan("CNC", "EQ-01", 202601, 50.0, 3.0)    // 150
        );
        when(productionPlanRepository.findAll()).thenReturn(plans);

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getWorkforceDemand()).isCloseTo(350.0, within(0.001));
    }

    /**
     * 不同期间产生不同行
     */
    @Test
    void differentPeriods_producesSeparateRows() {
        List<ProductionPlan> plans = List.of(
                makePlan("CNC", "EQ-01", 202601, 100.0, 2.0),
                makePlan("CNC", "EQ-01", 202602, 80.0, 2.0)
        );
        when(productionPlanRepository.findAll()).thenReturn(plans);

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).hasSize(2);
    }

    /**
     * 不同工序产生不同行
     */
    @Test
    void differentProcesses_producesSeparateRows() {
        List<ProductionPlan> plans = List.of(
                makePlan("CNC", "EQ-01", 202601, 100.0, 2.0),
                makePlan("WELD", "EQ-02", 202601, 100.0, 1.5)
        );
        when(productionPlanRepository.findAll()).thenReturn(plans);

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).hasSize(2);
    }

    // =========================================================================
    // 2. 过滤逻辑
    // =========================================================================

    /**
     * staffCount 为 null → 跳过该记录
     */
    @Test
    void planWithNullStaffCount_isExcluded() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("CNC", "EQ-01", 202601, 100.0, null)));

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).isEmpty();
    }

    /**
     * staffCount 为 0 → 跳过该记录
     */
    @Test
    void planWithZeroStaffCount_isExcluded() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("CNC", "EQ-01", 202601, 100.0, 0.0)));

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).isEmpty();
    }

    /**
     * planQty 为 null → 跳过该记录
     */
    @Test
    void planWithNullPlanQty_isExcluded() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan("CNC", "EQ-01", 202601, null, 2.0)));

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).isEmpty();
    }

    /**
     * process 为 null 的记录应被跳过（无工序数据无意义）
     */
    @Test
    void planWithNullProcess_isExcluded() {
        when(productionPlanRepository.findAll())
                .thenReturn(List.of(makePlan(null, "EQ-01", 202601, 100.0, 2.0)));

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // 3. 期间过滤
    // =========================================================================

    /**
     * 传入 periods 列表时，只按指定期间查询
     */
    @Test
    void withPeriodFilter_queriesByYearMonth() {
        List<Integer> periods = List.of(202601, 202602);
        when(productionPlanRepository.findByYearMonthIn(periods))
                .thenReturn(List.of(makePlan("CNC", "EQ-01", 202601, 100.0, 2.0)));

        List<WorkforceRow> result = service.calculateWorkforceReport(periods);

        verify(productionPlanRepository).findByYearMonthIn(periods);
        verify(productionPlanRepository, never()).findAll();
        assertThat(result).hasSize(1);
    }

    // =========================================================================
    // 4. 空结果
    // =========================================================================

    @Test
    void noPlans_returnsEmptyList() {
        when(productionPlanRepository.findAll()).thenReturn(Collections.emptyList());

        List<WorkforceRow> result = service.calculateWorkforceReport(null);

        assertThat(result).isEmpty();
    }
}
