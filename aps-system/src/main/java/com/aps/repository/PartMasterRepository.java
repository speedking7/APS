package com.aps.repository;

import com.aps.entity.PartMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartMasterRepository extends JpaRepository<PartMaster, Long> {

    Optional<PartMaster> findByPartNo(String partNo);

    List<PartMaster> findByPartNoIn(Collection<String> partNos);

    boolean existsByPartNo(String partNo);
}
