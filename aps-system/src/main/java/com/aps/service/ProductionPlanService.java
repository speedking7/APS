package com.aps.service;

import com.aps.entity.ProductionPlan;
import com.aps.repository.ProductionPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductionPlanService {

    @Autowired
    private ProductionPlanRepository repository;

    public List<ProductionPlan> findAll() {
        return repository.findAll();
    }

    public List<ProductionPlan> findByYearMonth(Integer yearMonth) {
        return repository.findByYearMonth(yearMonth);
    }

    public List<ProductionPlan> findByFinishedProductCode(String code) {
        return repository.findByFinishedProductCode(code);
    }

    public List<ProductionPlan> findByFinishedProductCodeAndYearMonth(String code, Integer yearMonth) {
        return repository.findByFinishedProductCodeAndYearMonth(code, yearMonth);
    }

    public List<String> findDistinctVersions() {
        return repository.findAll().stream()
                .map(ProductionPlan::getVersion)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<ProductionPlan> findByVersion(String version) {
        return repository.findByVersion(version);
    }
}
