package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * 稼动天数表 - 每月工作天数
 */
@Entity
@Table(name = "t_operating_days")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperatingDays {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 年月，例如 202604 */
    @Column(name = "`year_month`", nullable = false, unique = true)
    private Integer yearMonth;

    /** 天数 */
    @Column(nullable = false)
    private Double days;
}
