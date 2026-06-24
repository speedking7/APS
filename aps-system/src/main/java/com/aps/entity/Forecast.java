package com.aps.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * 预测表 - 完成品预测量
 */
@Entity
@Table(name = "t_forecast", uniqueConstraints = @UniqueConstraint(columnNames = {"item_code", "year_month"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Forecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 存货编码 */
    @Column(name = "item_code", length = 50, nullable = false)
    private String itemCode;

    /** 年月，例如 202604 */
    @Column(name = "year_month", nullable = false)
    private Integer yearMonth;

    /** 数量 */
    @Column(nullable = false)
    private Double quantity;

    /** 删除重写标记 */
    @Column(name = "delete_flag", length = 20)
    private String deleteFlag;
}
