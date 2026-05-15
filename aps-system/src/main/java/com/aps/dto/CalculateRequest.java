package com.aps.dto;

import lombok.Data;

@Data
public class CalculateRequest {
    private String version;             // 单一基础数据版本
    private String resultVersion;       // 结果版本（标识本次计算输出）
}
