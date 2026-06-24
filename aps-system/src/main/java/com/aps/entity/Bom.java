package com.aps.entity;

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
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 父零件 */
    @Column(name = "root_product_code", length = 50, nullable = false)
    private String rootProductCode;

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

    /** 制造部门 */
    @Column(name = "manufacturing_department", length = 50, nullable = false)
    private String manufacturingDepartment;

    /** 制造单元 */
    @Column(name = "manufacturing_unit", length = 50, nullable = false)
    private String manufacturingUnit;

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

    /** 报废率（该制造工步的报废率，0~1） */
    @Column(name = "scrap_rate")
    private Double scrapRate;

    /** 版本号 */
    @Column(name = "version", length = 50)
    private String version;

    public Bom(
            Long id,
            String parentCode,
            String childCode,
            Double usageQty,
            String process,
            String equipment,
            String manufacturingDepartment,
            String manufacturingUnit,
            Integer moldCavity,
            Double cycleTime,
            Double staffCount,
            Double taktTime,
            Double scrapRate,
            String version) {
        this.id = id;
        this.parentCode = parentCode;
        this.childCode = childCode;
        this.usageQty = usageQty;
        this.process = process;
        this.equipment = equipment;
        this.manufacturingDepartment = manufacturingDepartment;
        this.manufacturingUnit = manufacturingUnit;
        this.moldCavity = moldCavity;
        this.cycleTime = cycleTime;
        this.staffCount = staffCount;
        this.taktTime = taktTime;
        this.scrapRate = scrapRate;
        this.version = version;
    }

    public Bom(
            Long id,
            String rootProductCode,
            String parentCode,
            String childCode,
            Double usageQty,
            String process,
            String equipment,
            String manufacturingDepartment,
            String manufacturingUnit,
            Integer moldCavity,
            Double cycleTime,
            Double staffCount,
            Double taktTime,
            Double scrapRate,
            String version) {
        this(id, parentCode, childCode, usageQty, process, equipment, manufacturingDepartment,
                manufacturingUnit, moldCavity, cycleTime, staffCount, taktTime, scrapRate, version);
        this.rootProductCode = rootProductCode;
    }
}
