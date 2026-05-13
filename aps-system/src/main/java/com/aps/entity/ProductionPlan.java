package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * 生产计划结果集
 */
@Entity
@Table(name = "t_production_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 完成品编码 */
    @Column(name = "finished_product_code", length = 50, nullable = false)
    private String finishedProductCode;

    /** 存货编码 */
    @Column(name = "item_code", length = 50, nullable = false)
    private String itemCode;

    /** 年月 */
    @Column(name = "`year_month`", nullable = false)
    private Integer yearMonth;

    /** 工序 */
    @Column(length = 50)
    private String process;

    /** 设备 */
    @Column(length = 50)
    private String equipment;

    /** 模腔数 */
    @Column(name = "mold_cavity")
    private Integer moldCavity;

    /** 制造周期 */
    @Column(name = "cycle_time")
    private Double cycleTime;

    /** 持台人数 */
    @Column(name = "staff_count")
    private Double staffCount;

    /** 单件节拍 */
    @Column(name = "takt_time")
    private Double taktTime;

    /** 当前库存 */
    @Column(name = "current_inventory")
    private Double currentInventory;

    /** 预测/需求 */
    @Column(name = "forecast")
    private Double forecast;

    /** 库存天数（安全天数） */
    @Column(name = "safety_days")
    private Double safetyDays;

    /** 稼动天数 */
    @Column(name = "operating_days")
    private Double operatingDays;

    /** 报废率 */
    @Column(name = "scrap_rate")
    private Double scrapRate;

    /** 是否要生产 Y/N */
    @Column(name = "is_produce", length = 2)
    private String isProduce;

    /** 计划数量 */
    @Column(name = "plan_qty")
    private Double planQty;

    /** 计算版本号 */
    @Column(name = "version", length = 50)
    private String version;
}
