package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * 库存天数表 - 安全库存天数
 */
@Entity
@Table(name = "t_inventory_days")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDays {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 存货编码 */
    @Column(name = "item_code", length = 50, nullable = false, unique = true)
    private String itemCode;

    /** 安全天数 */
    @Column(name = "safety_days", nullable = false)
    private Double safetyDays;

    /** 最大天数 */
    @Column(name = "max_days")
    private Double maxDays;
}
