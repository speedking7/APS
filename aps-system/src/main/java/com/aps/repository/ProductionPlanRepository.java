package com.aps.repository;

import com.aps.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {

    List<ProductionPlan> findByFinishedProductCodeAndYearMonth(String finishedProductCode, Integer yearMonth);

    List<ProductionPlan> findByYearMonth(Integer yearMonth);

    List<ProductionPlan> findByFinishedProductCode(String finishedProductCode);

    List<ProductionPlan> findByYearMonthIn(List<Integer> yearMonths);
}
