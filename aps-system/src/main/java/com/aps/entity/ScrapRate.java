package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * 报废率表 - 各物料报废率
 */
@Entity
@Table(name = "t_scrap_rate")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrapRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 存货编码 */
    @Column(name = "item_code", length = 50, nullable = false, unique = true)
    private String itemCode;

    /** 报废率（小数，如 0.01 表示 1%） */
    @Column(name = "scrap_rate", nullable = false)
    private Double scrapRate;
}
