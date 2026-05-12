package com.aps.repository;

import com.aps.entity.Demand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandRepository extends JpaRepository<Demand, Long> {

    @Query("SELECT DISTINCT d.itemCode FROM Demand d")
    List<String> findDistinctItemCodes();

    @Query("SELECT DISTINCT d.yearMonth FROM Demand d ORDER BY d.yearMonth ASC")
    List<Integer> findDistinctYearMonths();

    Optional<Demand> findFirstByItemCodeAndYearMonth(String itemCode, Integer yearMonth);

    List<Demand> findByItemCode(String itemCode);

    void deleteByVersionAndCustomer(String version, String customer);
}
