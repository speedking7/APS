package com.aps.dto;

import lombok.Data;

@Data
public class CalculateRequest {
    private String demandVersion;       // 需求版本
    private String bomVersion;          // BOM 版本
    private String safetyStockVersion;  // 安全库存版本
    private String inventoryVersion;    // 盘点数版本
    private String resultVersion;       // 结果版本（标识本次计算输出）
}
