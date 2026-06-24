package com.aps.repository;

import com.aps.entity.ProductionPlan;

import java.util.List;

public interface ProductionPlanBatchRepository {

    void bulkInsert(List<ProductionPlan> plans);
}
