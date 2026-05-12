package com.aps.repository;

import com.aps.entity.InventoryCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryCountRepository extends JpaRepository<InventoryCount, Long> {

    /** 获取某物料最新的盘点数（按年月降序取第一条） */
    Optional<InventoryCount> findFirstByItemCodeOrderByYearMonthDesc(String itemCode);

    void deleteByVersion(String version);
}
