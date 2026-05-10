package com.aps.repository;

import com.aps.entity.OperatingDays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OperatingDaysRepository extends JpaRepository<OperatingDays, Long> {
    Optional<OperatingDays> findByYearMonth(Integer yearMonth);
}
