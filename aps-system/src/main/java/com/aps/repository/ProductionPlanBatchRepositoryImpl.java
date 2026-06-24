package com.aps.repository;

import com.aps.entity.ProductionPlan;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

@Repository
public class ProductionPlanBatchRepositoryImpl implements ProductionPlanBatchRepository {

    private static final String INSERT_SQL = "INSERT INTO t_production_plan ("
            + "calculated_at, current_inventory, cycle_time, equipment, finished_product_code, "
            + "forecast, is_produce, item_code, manufacturing_department, manufacturing_unit, "
            + "mold_cavity, operating_days, plan_qty, raw_plan_qty, process, safety_days, scrap_rate, "
            + "staff_count, takt_time, version, `year_month`) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public ProductionPlanBatchRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void bulkInsert(List<ProductionPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ProductionPlan plan = plans.get(i);
                ps.setObject(1, plan.getCalculatedAt());
                setNullableDouble(ps, 2, plan.getCurrentInventory());
                setNullableDouble(ps, 3, plan.getCycleTime());
                ps.setString(4, plan.getEquipment());
                ps.setString(5, plan.getFinishedProductCode());
                setNullableDouble(ps, 6, plan.getForecast());
                ps.setString(7, plan.getIsProduce());
                ps.setString(8, plan.getItemCode());
                ps.setString(9, plan.getManufacturingDepartment());
                ps.setString(10, plan.getManufacturingUnit());
                setNullableInteger(ps, 11, plan.getMoldCavity());
                setNullableDouble(ps, 12, plan.getOperatingDays());
                setNullableDouble(ps, 13, plan.getPlanQty());
                setNullableDouble(ps, 14, plan.getRawPlanQty());
                ps.setString(15, plan.getProcess());
                setNullableDouble(ps, 16, plan.getSafetyDays());
                setNullableDouble(ps, 17, plan.getScrapRate());
                setNullableDouble(ps, 18, plan.getStaffCount());
                setNullableDouble(ps, 19, plan.getTaktTime());
                ps.setString(20, plan.getVersion());
                setNullableInteger(ps, 21, plan.getYearMonth());
            }

            @Override
            public int getBatchSize() {
                return plans.size();
            }
        });
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    private void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
