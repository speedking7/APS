package com.aps.service;

import com.aps.entity.EquipmentCatalog;
import com.aps.entity.OperatingDays;
import com.aps.entity.ProductionPlan;
import com.aps.repository.EquipmentCatalogRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EquipmentLoadService 单元测试
 *
 * 当前目标口径：
 *   requiredSeconds      = planQty × cycleTime / moldCavity
 *   requiredMachineCount = Σ(requiredSeconds / availableSeconds)
 *   availableSeconds     = workDays × 10.5 × 3600
 *   loadRate             = requiredMachineCount / equipmentCount
 *
 * 台账匹配规则：
 *   manufacturingDepartment + equipment(=equipmentModel)
 */
@ExtendWith(MockitoExtension.class)
class EquipmentLoadServiceTest {

    @InjectMocks
    private EquipmentLoadService service;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private OperatingDaysRepository operatingDaysRepository;

    @Mock
    private EquipmentCatalogRepository equipmentCatalogRepository;

    @Mock
    private SharedMoldRuleService sharedMoldRuleService;

    private ProductionPlan makePlan(
            String version,
            String finishedProductCode,
            String itemCode,
            String equipment,
            String process,
            String manufacturingDepartment,
            Integer yearMonth,
            Double planQty,
            Integer moldCavity,
            Double cycleTime
    ) {
        ProductionPlan p = new ProductionPlan();
        p.setVersion(version);
        p.setFinishedProductCode(finishedProductCode);
        p.setItemCode(itemCode);
        p.setEquipment(equipment);
        p.setProcess(process);
        p.setManufacturingDepartment(manufacturingDepartment);
        p.setYearMonth(yearMonth);
        p.setPlanQty(planQty);
        p.setMoldCavity(moldCavity);
        p.setCycleTime(cycleTime);
        return p;
    }

    private EquipmentCatalog makeCatalog(
            String department,
            String category,
            String brand,
            String model,
            Integer count
    ) {
        EquipmentCatalog catalog = new EquipmentCatalog();
        catalog.setManufacturingDepartment(department);
        catalog.setEquipmentCategory(category);
        catalog.setEquipmentBrand(brand);
        catalog.setEquipmentModel(model);
        catalog.setEquipmentCount(count);
        return catalog;
    }

    @Test
    void matchedCatalog_usesCycleTimeMoldCavityAndCatalogFields() {
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "FP001", "FP001", "aa001", "冲压", "制造一部", 202601, 3600.0, 2, 10.0)
        ));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(Optional.of(makeCatalog("制造一部", "冲压设备", "AIDA", "aa001", 4)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        EquipmentLoadRow row = result.get(0);
        assertThat(row.getManufacturingDepartment()).isEqualTo("制造一部");
        assertThat(row.getEquipmentCategory()).isEqualTo("冲压设备");
        assertThat(row.getEquipmentBrand()).isEqualTo("AIDA");
        assertThat(row.getEquipmentModel()).isEqualTo("aa001");
        assertThat(row.getEquipmentCount()).isEqualTo(4);
        assertThat(row.getYearMonth()).isEqualTo(202601);
        assertThat(row.getWorkDays()).isCloseTo(22.0, within(0.001));
        assertThat(row.getMatchedCatalog()).isTrue();
        assertThat(row.getPlanQty()).isCloseTo(3600.0, within(0.000001));
        assertThat(row.getCycleTime()).isCloseTo(10.0, within(0.000001));
        assertThat(row.getMoldCavity()).isEqualTo(2);
        assertThat(row.getDailyHours()).isCloseTo(10.5, within(0.000001));

        double availableSeconds = 22.0 * 10.5 * 3600.0;
        double requiredSeconds = (3600.0 * 10.0) / 2.0;
        assertThat(row.getRequiredSeconds()).isCloseTo(requiredSeconds, within(0.000001));
        assertThat(row.getAvailableSecondsPerMachine()).isCloseTo(availableSeconds, within(0.000001));
        assertThat(row.getAvailableSecondsTotal()).isCloseTo(availableSeconds * 4.0, within(0.000001));
        double requiredMachineCount = requiredSeconds / availableSeconds;
        assertThat(row.getRequiredMachineCount()).isCloseTo(requiredMachineCount, within(0.000001));
        assertThat(row.getDifference()).isCloseTo(4.0 - requiredMachineCount, within(0.000001));
        assertThat(row.getLoadRate()).isCloseTo(requiredMachineCount / 4.0, within(0.000001));

        assertThat(row.getTaskTimeHours()).isCloseTo(requiredSeconds / 3600.0, within(0.000001));
        assertThat(row.getAvailableTimeHours()).isCloseTo(22.0 * 10.5, within(0.000001));
        assertThat(row.getUtilizationRate()).isCloseTo(requiredMachineCount / 4.0, within(0.000001));
        assertThat(row.getStatus()).isEqualTo("LOOSE");
    }

    @Test
    void unmatchedCatalog_fallsBackToDefaults() {
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "FP001", "FP001", "eq-x1", "焊接", "制造二部", 202601, 7200.0, 4, 8.0)
        ));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 20.0, 20.0, 0.0, 0.0)));
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造二部", "eq-x1"))
                .thenReturn(Optional.empty());

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        EquipmentLoadRow row = result.get(0);
        assertThat(row.getEquipmentCategory()).isEqualTo("焊接");
        assertThat(row.getEquipmentBrand()).isEqualTo("—");
        assertThat(row.getEquipmentModel()).isEqualTo("eq-x1");
        assertThat(row.getEquipmentCount()).isEqualTo(1);
        assertThat(row.getMatchedCatalog()).isFalse();
        assertThat(row.getDailyHours()).isCloseTo(10.5, within(0.000001));
    }

    @Test
    void multiplePlansForSameDepartmentModelAndMonth_sumRequiredMachineCount() {
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "FP001", "FP001", "aa001", "冲压", "制造一部", 202601, 3600.0, 2, 10.0),
                makePlan("v1", "FP002", "FP002", "aa001", "冲压", "制造一部", 202601, 1800.0, 2, 10.0)
        ));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(Optional.of(makeCatalog("制造一部", "冲压设备", "AIDA", "aa001", 2)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        double availableSeconds = 22.0 * 10.5 * 3600.0;
        double expectedRequiredSeconds = (3600.0 * 10.0) / 2.0 + (1800.0 * 10.0) / 2.0;
        assertThat(result.get(0).getRequiredMachineCount())
                .isCloseTo(expectedRequiredSeconds / availableSeconds, within(0.000001));
    }

    @Test
    void differentDepartmentsWithSameEquipmentModel_produceSeparateRows() {
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "FP001", "FP001", "aa001", "冲压", "制造一部", 202601, 3600.0, 2, 10.0),
                makePlan("v1", "FP002", "FP002", "aa001", "冲压", "制造二部", 202601, 3600.0, 2, 10.0)
        ));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(Optional.of(makeCatalog("制造一部", "冲压设备", "AIDA", "aa001", 2)));
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造二部", "aa001"))
                .thenReturn(Optional.of(makeCatalog("制造二部", "冲压设备", "AIDA", "aa001", 3)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(2);
    }

    @Test
    void moldCavityNullOrZero_treatedAsOne() {
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "FP001", "FP001", "aa001", "冲压", "制造一部", 202601, 3600.0, 0, 10.0)
        ));
        when(operatingDaysRepository.findByYearMonth(202601))
                .thenReturn(Optional.of(new OperatingDays(null, 202601, 22.0, 22.0, 0.0, 0.0)));
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(Optional.of(makeCatalog("制造一部", "冲压设备", "AIDA", "aa001", 1)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        double availableSeconds = 22.0 * 10.5 * 3600.0;
        double expectedRequiredSeconds = 3600.0 * 10.0;
        assertThat(result.get(0).getRequiredMachineCount())
                .isCloseTo(expectedRequiredSeconds / availableSeconds, within(0.000001));
    }

    @Test
    void noOperatingDays_availableTimeIsZeroAndStatusIsLoose() {
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "FP001", "FP001", "aa001", "冲压", "制造一部", 202601, 3600.0, 2, 10.0)
        ));
        when(operatingDaysRepository.findByYearMonth(202601)).thenReturn(Optional.empty());
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(Optional.of(makeCatalog("制造一部", "冲压设备", "AIDA", "aa001", 2)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getWorkDays()).isEqualTo(0.0);
        assertThat(result.get(0).getAvailableTimeHours()).isEqualTo(0.0);
        assertThat(result.get(0).getRequiredMachineCount()).isEqualTo(0.0);
        assertThat(result.get(0).getLoadRate()).isEqualTo(0.0);
        assertThat(result.get(0).getStatus()).isEqualTo("LOOSE");
    }

    @Test
    void planWithNullEquipment_isExcluded() {
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "FP001", "FP001", null, "冲压", "制造一部", 202601, 3600.0, 2, 10.0)
        ));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).isEmpty();
    }

    @Test
    void planWithNullCycleTime_isExcluded() {
        ProductionPlan plan = makePlan("v1", "FP001", "FP001", "aa001", "冲压", "制造一部", 202601, 3600.0, 2, null);
        when(productionPlanRepository.findAll()).thenReturn(List.of(plan));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).isEmpty();
    }

    @Test
    void planWithNullPlanQty_isExcluded() {
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "FP001", "FP001", "aa001", "冲压", "制造一部", 202601, null, 2, 10.0)
        ));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).isEmpty();
    }

    @Test
    void withPeriodFilter_queriesByYearMonthIn() {
        List<Integer> periods = List.of(202601);
        when(productionPlanRepository.findByYearMonthIn(periods)).thenReturn(Collections.emptyList());

        service.calculateEquipmentLoad(periods);

        verify(productionPlanRepository).findByYearMonthIn(periods);
        verify(productionPlanRepository, never()).findAll();
    }

    @Test
    void withVersionFilter_queriesByVersion() {
        when(productionPlanRepository.findByVersion("v1")).thenReturn(Collections.emptyList());

        service.calculateEquipmentLoad(null, "v1");

        verify(productionPlanRepository).findByVersion("v1");
        verify(productionPlanRepository, never()).findAll();
        verify(productionPlanRepository, never()).findByYearMonthIn(anyList());
    }

    @Test
    void noPlans_returnsEmptyList() {
        when(productionPlanRepository.findAll()).thenReturn(Collections.emptyList());

        assertThat(service.calculateEquipmentLoad(null)).isEmpty();
    }

    @Test
    void sharedMoldPair_keepsSuppressedRowButZerosItsLoadContribution() {
        when(sharedMoldRuleService.findEnabledRules()).thenReturn(List.of(
                new com.aps.entity.SharedMoldRule(1L, "203000324D", "203000326D", null, null, true, null),
                new com.aps.entity.SharedMoldRule(2L, "203000328D", "203000330D", null, null, true, null)
        ));
        when(productionPlanRepository.findAll()).thenReturn(List.of(
                makePlan("v1", "203000324D", "203000324D", "aa001", "加饰注塑", "制造一部", 202606, 120.0, 2, 10.0),
                makePlan("v1", "203000326D", "203000326D", "aa001", "加饰注塑", "制造一部", 202606, 80.0, 2, 10.0),
                makePlan("v1", "203000324D", "206100001D", "aa001", "加饰注塑", "制造一部", 202606, 60.0, 2, 10.0)
        ));
        when(operatingDaysRepository.findByYearMonth(202606))
                .thenReturn(Optional.of(new OperatingDays(null, 202606, 22.0, 22.0, 0.0, 0.0)));
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(Optional.of(makeCatalog("制造一部", "注塑设备", "TEST", "aa001", 1)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        double availableSeconds = 22.0 * 10.5 * 3600.0;
        double expectedRequiredSeconds = (120.0 * 10.0) / 2.0 + (60.0 * 10.0) / 2.0;
        assertThat(result.get(0).getRequiredSeconds()).isCloseTo(expectedRequiredSeconds, within(0.000001));
        assertThat(result.get(0).getRequiredMachineCount()).isCloseTo(expectedRequiredSeconds / availableSeconds, within(0.000001));
        assertThat(result.get(0).getSharedMoldAdjusted()).isTrue();
    }

    @Test
    void sharedMoldPair_whenPlanQtyAlreadyAligned_suppressesOneRowForEquipmentLoad() {
        when(sharedMoldRuleService.findEnabledRules()).thenReturn(List.of(
                new com.aps.entity.SharedMoldRule(1L, "203000324D", "203000326D", null, null, true, null)
        ));
        ProductionPlan a = makePlan("v1", "203000324D", "203000324D", "aa001", "加饰注塑", "制造一部", 202606, 120.0, 2, 10.0);
        a.setRawPlanQty(80.0);
        ProductionPlan b = makePlan("v1", "203000326D", "203000326D", "aa001", "加饰注塑", "制造一部", 202606, 120.0, 2, 10.0);
        b.setRawPlanQty(120.0);
        when(productionPlanRepository.findAll()).thenReturn(List.of(a, b));
        when(operatingDaysRepository.findByYearMonth(202606))
                .thenReturn(Optional.of(new OperatingDays(null, 202606, 22.0, 22.0, 0.0, 0.0)));
        when(equipmentCatalogRepository.findByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(Optional.of(makeCatalog("制造一部", "注塑设备", "TEST", "aa001", 1)));

        List<EquipmentLoadRow> result = service.calculateEquipmentLoad(null);

        assertThat(result).hasSize(1);
        double expectedRequiredSeconds = (120.0 * 10.0) / 2.0;
        assertThat(result.get(0).getRequiredSeconds()).isCloseTo(expectedRequiredSeconds, within(0.000001));
        assertThat(result.get(0).getDetailRows())
                .filteredOn(row -> Boolean.TRUE.equals(row.getSharedMoldSuppressed()))
                .hasSize(1)
                .allMatch(row -> "203000324D".equals(row.getItemCode()))
                .allMatch(row -> row.getRequiredSecondsEffective() == 0.0);
        assertThat(result.get(0).getDetailRows())
                .filteredOn(row -> !Boolean.TRUE.equals(row.getSharedMoldSuppressed()))
                .hasSize(1)
                .allMatch(row -> "203000326D".equals(row.getItemCode()))
                .allMatch(row -> row.getRequiredSecondsEffective() > 0.0);
    }
}
