package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * 盘点数表 - 期初库存
 */
@Entity
@Table(name = "t_inventory_count", uniqueConstraints = @UniqueConstraint(columnNames = {"item_code", "year_month"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 存货编码 */
    @Column(name = "item_code", length = 50, nullable = false)
    private String itemCode;

    /** 年月（期末） */
    @Column(name = "`year_month`", nullable = false)
    private Integer yearMonth;

    /** 可用量 */
    @Column(name = "available_qty", nullable = false)
    private Double availableQty;

    /** 版本号 */
    @Column(name = "version", length = 50)
    private String version;
}
