package com.aps.controller;

import com.aps.common.ApiResponse;
import com.aps.service.EquipmentLoadRow;
import com.aps.service.EquipmentLoadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备负荷测算报表接口（FUNC-EL-003/004）
 */
@RestController
@RequestMapping("/api/equipment-load")
@CrossOrigin
public class EquipmentLoadController {

    @Autowired
    private EquipmentLoadService equipmentLoadService;

    /**
     * 获取设备负荷报表
     *
     * @param periods 期间列表（YYYYMM，可选，不传则查询全部）
     * @param version 版本号（可选，传入时优先按版本查询）
     */
    @GetMapping
    public ApiResponse<List<EquipmentLoadRow>> getReport(
            @RequestParam(required = false) List<Integer> periods,
            @RequestParam(required = false) String version) {
        return ApiResponse.success(equipmentLoadService.calculateEquipmentLoad(periods, version));
    }
}
