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
    @Column(name = "year_month", nullable = false, unique = true)
    private Integer yearMonth;

    /** 总出勤天数 */
    @Column(name = "total_days")
    private Double totalDays;

    /** 工作日（剔除双休日和节假日，计划计算使用此字段） */
    @Column(name = "work_days", nullable = false)
    private Double workDays;

    /** 双休日天数 */
    @Column(name = "weekend_days")
    private Double weekendDays;

    /** 国定节假日天数 */
    @Column(name = "holiday_days")
    private Double holidayDays;
}
