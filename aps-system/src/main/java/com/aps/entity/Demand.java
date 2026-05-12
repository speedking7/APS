package com.aps.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;

/**
 * 完成品入库需求数
 */
@Entity
@Table(name = "t_demand")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Demand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户 */
    @Column(name = "customer", length = 100)
    private String customer;

    /** 存货编码 */
    @Column(name = "item_code", length = 50, nullable = false)
    private String itemCode;

    /** 年月，YYYYMM */
    @Column(name = "`year_month`", nullable = false)
    private Integer yearMonth;

    /** 需求数量（客户原始需求） */
    @Column(name = "demand_qty")
    private Double demandQty;

    /** 期末库存 */
    @Column(name = "ending_inventory")
    private Double endingInventory;

    /** 最小安全库存 */
    @Column(name = "min_safety_stock")
    private Double minSafetyStock;

    /** 完成品入库需求数（计划计算的实际依据） */
    @Column(name = "net_demand")
    private Double netDemand;

    /** 版本号 */
    @Column(name = "version", length = 50)
    private String version;
}
