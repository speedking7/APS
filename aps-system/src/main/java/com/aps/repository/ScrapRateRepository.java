package com.aps.repository;

import com.aps.entity.ScrapRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScrapRateRepository extends JpaRepository<ScrapRate, Long> {
    Optional<ScrapRate> findByItemCode(String itemCode);
}
