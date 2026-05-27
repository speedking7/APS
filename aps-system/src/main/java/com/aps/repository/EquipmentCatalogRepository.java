package com.aps.repository;

import com.aps.entity.EquipmentCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EquipmentCatalogRepository extends JpaRepository<EquipmentCatalog, Long> {

    boolean existsByManufacturingDepartmentAndEquipmentModel(String manufacturingDepartment, String equipmentModel);

    Optional<EquipmentCatalog> findByManufacturingDepartmentAndEquipmentModel(String manufacturingDepartment, String equipmentModel);

    List<EquipmentCatalog> findByManufacturingDepartmentIn(Collection<String> departments);

    void deleteByManufacturingDepartmentIn(Set<String> departments);
}
