package com.aps.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;

/**
 * 半成品安全库存
 */
@Entity
@Table(name = "t_safety_stock", uniqueConstraints = @UniqueConstraint(columnNames = {"item_code", "year_month", "version"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SafetyStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 存货编码 */
    @Column(name = "item_code", length = 50, nullable = false)
    private String itemCode;

    /** 年月，格式 YYYYMM */
    @Column(name = "year_month", nullable = false)
    private Integer yearMonth;

    /** 每日当量 */
    @Column(name = "daily_equivalent")
    private Double dailyEquivalent;

    /** 安全天数 */
    @Column(name = "safety_days", nullable = false)
    private Double safetyDays;

    /** 最大天数 */
    @Column(name = "max_days")
    private Double maxDays;

    /** 版本号 */
    @Column(name = "version", length = 50)
    private String version;
}
