package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * BOM 表 - 物料清单
 */
@Entity
@Table(name = "t_bom")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 父零件 */
    @Column(name = "parent_code", length = 50, nullable = false)
    private String parentCode;

    /** 子零件（叶节点可为 null） */
    @Column(name = "child_code", length = 50)
    private String childCode;

    /** 用量 */
    @Column(name = "usage_qty")
    private Double usageQty;

    /** 工序 */
    @Column(length = 50)
    private String process;

    /** 设备 */
    @Column(length = 50)
    private String equipment;

    /** 模腔数 (pcs) */
    @Column(name = "mold_cavity")
    private Integer moldCavity;

    /** 制造周期 (S) */
    @Column(name = "cycle_time")
    private Double cycleTime;

    /** 持台人数 (人) */
    @Column(name = "staff_count")
    private Double staffCount;

    /** 单件节拍 (S) */
    @Column(name = "takt_time")
    private Double taktTime;
}
