package com.aps.repository;

import com.aps.entity.Bom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BomRepository extends JpaRepository<Bom, Long> {

    /** 获取某物料的所有子件 BOM 行 */
    List<Bom> findByParentCode(String parentCode);

    /** 获取某物料作为父零件的工序信息（含叶节点） */
    Optional<Bom> findFirstByParentCode(String parentCode);
}
