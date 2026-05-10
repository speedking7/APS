package com.aps.service;

import com.aps.entity.ProductionPlan;
import com.aps.repository.ProductionPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 一线人员需求测算服务（FUNC-CW-003）
 *
 * 计算逻辑：
 *   workforceDemand = planQty × staffCount
 *   按 (process, equipment, yearMonth) 聚合求和
 *   跳过 process/planQty/staffCount 为 null 或 staffCount=0 的记录
 */
@Service
@Transactional(readOnly = true)
public class WorkforceReportService {

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    /**
     * 计算人员需求报表
     *
     * @param periods 期间列表（YYYYMM），为 null 时查询全部
     * @return 聚合后的人员需求行列表，按 yearMonth, process 升序
     */
    public List<WorkforceRow> calculateWorkforceReport(List<Integer> periods) {
        List<ProductionPlan> plans = (periods == null || periods.isEmpty())
                ? productionPlanRepository.findAll()
                : productionPlanRepository.findByYearMonthIn(periods);

        Map<String, WorkforceRow> aggregated = new LinkedHashMap<>();

        for (ProductionPlan p : plans) {
            if (p.getProcess() == null) continue;
            if (p.getPlanQty() == null) continue;
            if (p.getStaffCount() == null || p.getStaffCount() == 0.0) continue;

            String key = p.getProcess() + "|" + p.getEquipment() + "|" + p.getYearMonth();
            WorkforceRow row = aggregated.computeIfAbsent(key,
                    k -> new WorkforceRow(p.getProcess(), p.getEquipment(), p.getYearMonth(), 0.0));
            row.setWorkforceDemand(row.getWorkforceDemand() + p.getPlanQty() * p.getStaffCount());
        }

        List<WorkforceRow> result = new ArrayList<>(aggregated.values());
        result.sort(Comparator.comparingInt(WorkforceRow::getYearMonth)
                .thenComparing(WorkforceRow::getProcess));
        return result;
    }
}
