package com.aps.service;

import com.aps.dto.ProductionPlanView;
import com.aps.entity.PartMaster;
import com.aps.entity.ProductionPlan;
import com.aps.repository.PartMasterRepository;
import com.aps.repository.ProductionPlanRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class ProductionPlanService {

    @Autowired
    private ProductionPlanRepository repository;

    @Autowired
    private PartMasterRepository partMasterRepository;

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

    public List<ProductionPlanView> findAllViews() {
        return toViews(repository.findAll());
    }

    public List<ProductionPlanView> findViewsByVersion(String version) {
        return toViews(repository.findByVersion(version));
    }

    public List<ProductionPlanView> findViewsByYearMonth(Integer yearMonth) {
        return toViews(repository.findByYearMonth(yearMonth));
    }

    public List<ProductionPlanView> findViewsByFinishedProductCode(String code) {
        return toViews(repository.findByFinishedProductCode(code));
    }

    public List<ProductionPlanView> findViewsByFinishedProductCodeAndYearMonth(String code, Integer yearMonth) {
        return toViews(repository.findByFinishedProductCodeAndYearMonth(code, yearMonth));
    }

    private List<ProductionPlanView> toViews(List<ProductionPlan> plans) {
        Set<String> partNos = plans.stream()
                .flatMap(p -> Stream.of(p.getItemCode(), p.getFinishedProductCode()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, PartMaster> masterMap = loadPartMasterMap(partNos);

        return plans.stream().map(plan -> {
            ProductionPlanView view = new ProductionPlanView();
            BeanUtils.copyProperties(plan, view);

            PartMaster item = masterMap.get(plan.getItemCode());
            if (item != null) {
                view.setItemProductName(item.getProductName());
                view.setItemProductNo(item.getProductNo());
                view.setItemProjectName(item.getProjectName());
            }

            PartMaster finished = masterMap.get(plan.getFinishedProductCode());
            if (finished != null) {
                view.setFinishedProductName(finished.getProductName());
                view.setFinishedProductNo(finished.getProductNo());
                view.setFinishedProjectName(finished.getProjectName());
            }

            return view;
        }).collect(Collectors.toList());
    }

    private Map<String, PartMaster> loadPartMasterMap(Collection<String> partNos) {
        if (partNos == null || partNos.isEmpty()) {
            return Map.of();
        }
        return partMasterRepository.findByPartNoIn(partNos).stream()
                .collect(Collectors.toMap(PartMaster::getPartNo, Function.identity()));
    }
}
