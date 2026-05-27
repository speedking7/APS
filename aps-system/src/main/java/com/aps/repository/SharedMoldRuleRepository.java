package com.aps.repository;

import com.aps.entity.SharedMoldRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedMoldRuleRepository extends JpaRepository<SharedMoldRule, Long> {

    boolean existsByProductACodeAndProductBCode(String productACode, String productBCode);

    List<SharedMoldRule> findByEnabledTrue();
}
