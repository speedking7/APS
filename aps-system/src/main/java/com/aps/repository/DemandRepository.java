package com.aps.repository;

import com.aps.entity.Demand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandRepository extends JpaRepository<Demand, Long> {

    @Query("SELECT DISTINCT d.itemCode FROM Demand d")
    List<String> findDistinctItemCodes();

    @Query("SELECT DISTINCT d.yearMonth FROM Demand d ORDER BY d.yearMonth ASC")
    List<Integer> findDistinctYearMonths();

    @Query("SELECT DISTINCT d.itemCode FROM Demand d WHERE d.version = :version")
    List<String> findDistinctItemCodesByVersion(@Param("version") String version);

    @Query("SELECT DISTINCT d.yearMonth FROM Demand d WHERE d.version = :version ORDER BY d.yearMonth")
    List<Integer> findDistinctYearMonthsByVersion(@Param("version") String version);

    List<Demand> findByVersion(String version);

    Optional<Demand> findFirstByItemCodeAndYearMonth(String itemCode, Integer yearMonth);

    Optional<Demand> findFirstByItemCodeAndYearMonthAndVersion(String itemCode, Integer yearMonth, String version);

    List<Demand> findByItemCode(String itemCode);

    void deleteByVersionAndCustomer(String version, String customer);
}
