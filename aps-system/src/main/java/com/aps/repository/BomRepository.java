package com.aps.repository;

import com.aps.entity.Bom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BomRepository extends JpaRepository<Bom, Long> {

    /** 获取某物料的所有子件 BOM 行 */
    List<Bom> findByParentCode(String parentCode);

    /** 获取某物料作为父零件的工序信息（含叶节点） */
    Optional<Bom> findFirstByParentCode(String parentCode);

    /** 获取某版本下某物料的所有子件 BOM 行 */
    List<Bom> findByParentCodeAndVersion(String parentCode, String version);

    /** 获取某版本下某物料作为父零件的工序信息 */
    Optional<Bom> findFirstByParentCodeAndVersion(String parentCode, String version);

    @Modifying
    @Query("DELETE FROM Bom b WHERE b.version = :version")
    void deleteByVersion(String version);
}
