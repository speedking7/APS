package com.aps.repository;

import com.aps.entity.SafetyStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SafetyStockRepository extends JpaRepository<SafetyStock, Long> {

    Optional<SafetyStock> findByItemCode(String itemCode);

    void deleteByVersion(String version);
}
