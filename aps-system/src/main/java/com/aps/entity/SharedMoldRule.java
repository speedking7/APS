package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "t_shared_mold_rule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shared_mold_rule_product_pair",
                columnNames = {"product_a_code", "product_b_code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedMoldRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_a_code", length = 50, nullable = false)
    private String productACode;

    @Column(name = "product_b_code", length = 50, nullable = false)
    private String productBCode;

    @Column(name = "equipment_code", length = 100)
    private String equipmentCode;

    @Column(name = "mold_code", length = 100)
    private String moldCode;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "remark", length = 255)
    private String remark;
}
