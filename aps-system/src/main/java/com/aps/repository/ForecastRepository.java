package com.aps.repository;

import com.aps.entity.Forecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ForecastRepository extends JpaRepository<Forecast, Long> {

    @Query("SELECT DISTINCT f.itemCode FROM Forecast f")
    List<String> findDistinctItemCodes();

    Optional<Forecast> findFirstByItemCodeAndYearMonth(String itemCode, Integer yearMonth);

    @Query("SELECT DISTINCT f.yearMonth FROM Forecast f ORDER BY f.yearMonth ASC")
    List<Integer> findDistinctYearMonths();

    List<Forecast> findByItemCode(String itemCode);
}
