package com.aps.repository;

import com.aps.entity.SafetyStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SafetyStockRepository extends JpaRepository<SafetyStock, Long> {

    List<SafetyStock> findByItemCode(String itemCode);

    Optional<SafetyStock> findByItemCodeAndYearMonthAndVersion(String itemCode, Integer yearMonth, String version);

    Optional<SafetyStock> findFirstByItemCodeAndVersion(String itemCode, String version);

    @Modifying
    @Query("DELETE FROM SafetyStock s WHERE s.version = :version")
    void deleteByVersion(String version);
}
