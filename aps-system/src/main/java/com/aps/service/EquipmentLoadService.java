package com.aps.service;

import com.aps.entity.ProductionPlan;
import com.aps.repository.OperatingDaysRepository;
import com.aps.repository.ProductionPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 设备负荷测算服务（FUNC-EL-003/004）
 *
 * 计算逻辑：
 *   taskTimeHours     = sum(planQty × taktTime / 3600)，按 (equipment, process, yearMonth) 聚合
 *   availableTimeHours = operatingDays × DEFAULT_HOURS_PER_DAY
 *   utilizationRate   = taskTimeHours / availableTimeHours（availableTime=0 时取 0）
 *   status            = LOOSE / NORMAL / TIGHT / OVERLOADED
 */
@Service
@Transactional(readOnly = true)
public class EquipmentLoadService {

    static final double DEFAULT_HOURS_PER_DAY = 10.5;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Autowired
    private OperatingDaysRepository operatingDaysRepository;

    /**
     * 计算设备负荷报表
     *
     * @param periods 期间列表（YYYYMM），为 null 时查询全部
     * @return 聚合后的设备负荷行列表，按 yearMonth, equipment 升序
     */
    public List<EquipmentLoadRow> calculateEquipmentLoad(List<Integer> periods) {
        List<ProductionPlan> plans = (periods == null || periods.isEmpty())
                ? productionPlanRepository.findAll()
                : productionPlanRepository.findByYearMonthIn(periods);

        // 按 (equipment, process, yearMonth) 累加任务时间
        Map<String, double[]> taskTimeMap = new LinkedHashMap<>();
        Map<String, EquipmentLoadRow> rowMap = new LinkedHashMap<>();

        for (ProductionPlan p : plans) {
            if (p.getEquipment() == null) continue;
            if (p.getTaktTime() == null) continue;
            if (p.getPlanQty() == null) continue;

            String key = p.getEquipment() + "|" + p.getProcess() + "|" + p.getYearMonth();
            rowMap.computeIfAbsent(key, k -> {
                EquipmentLoadRow r = new EquipmentLoadRow();
                r.setEquipment(p.getEquipment());
                r.setProcess(p.getProcess());
                r.setYearMonth(p.getYearMonth());
                r.setTaskTimeHours(0.0);
                return r;
            });

            double taskHours = p.getPlanQty() * p.getTaktTime() / 3600.0;
            EquipmentLoadRow row = rowMap.get(key);
            row.setTaskTimeHours(row.getTaskTimeHours() + taskHours);
        }

        // 填充可用时间、利用率、状态
        for (EquipmentLoadRow row : rowMap.values()) {
            double opDays = operatingDaysRepository.findByYearMonth(row.getYearMonth())
                    .map(od -> od.getDays()).orElse(0.0);
            double available = opDays * DEFAULT_HOURS_PER_DAY;
            row.setAvailableTimeHours(available);

            double rate = (available > 0) ? row.getTaskTimeHours() / available : 0.0;
            row.setUtilizationRate(rate);
            row.setStatus(resolveStatus(rate));
        }

        List<EquipmentLoadRow> result = new ArrayList<>(rowMap.values());
        result.sort(Comparator.comparingInt(EquipmentLoadRow::getYearMonth)
                .thenComparing(EquipmentLoadRow::getEquipment));
        return result;
    }

    private String resolveStatus(double rate) {
        if (rate >= 1.0) return "OVERLOADED";
        if (rate >= 0.85) return "TIGHT";
        if (rate >= 0.50) return "NORMAL";
        return "LOOSE";
    }
}
