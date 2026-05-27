package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "t_equipment_catalog",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_equipment_catalog_dept_model",
                columnNames = {"manufacturing_department", "equipment_model"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manufacturing_department", length = 50, nullable = false)
    private String manufacturingDepartment;

    @Column(name = "equipment_category", length = 100, nullable = false)
    private String equipmentCategory;

    @Column(name = "equipment_brand", length = 100, nullable = false)
    private String equipmentBrand;

    @Column(name = "equipment_model", length = 100, nullable = false)
    private String equipmentModel;

    @Column(name = "equipment_count", nullable = false)
    private Integer equipmentCount;
}
