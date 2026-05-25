package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "t_part_master", uniqueConstraints = @UniqueConstraint(columnNames = "part_no"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "part_no", length = 50, nullable = false)
    private String partNo;

    @Column(name = "product_name", length = 200, nullable = false)
    private String productName;

    @Column(name = "product_no", length = 100, nullable = false)
    private String productNo;

    @Column(name = "project_name", length = 100, nullable = false)
    private String projectName;
}
