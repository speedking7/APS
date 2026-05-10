package com.aps.repository;

import com.aps.entity.InventoryDays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryDaysRepository extends JpaRepository<InventoryDays, Long> {
    Optional<InventoryDays> findByItemCode(String itemCode);
}
