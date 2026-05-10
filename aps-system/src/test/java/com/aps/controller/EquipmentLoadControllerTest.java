package com.aps.controller;

import com.aps.service.EquipmentLoadRow;
import com.aps.service.EquipmentLoadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EquipmentLoadController.class)
class EquipmentLoadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EquipmentLoadService equipmentLoadService;

    // -------------------------------------------------------------------------
    // GET /api/equipment-load  （无 period 参数 → 全量查询）
    // -------------------------------------------------------------------------

    @Test
    void getAll_noParams_returnsAllRows() throws Exception {
        List<EquipmentLoadRow> rows = List.of(
                new EquipmentLoadRow("EQ-01", "CNC", 202601, 1.0, 231.0, 0.0043, "LOOSE"),
                new EquipmentLoadRow("EQ-02", "WELD", 202601, 200.0, 231.0, 0.866, "TIGHT"));
        when(equipmentLoadService.calculateEquipmentLoad(null)).thenReturn(rows);

        mockMvc.perform(get("/api/equipment-load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].equipment").value("EQ-01"))
                .andExpect(jsonPath("$.data[0].status").value("LOOSE"))
                .andExpect(jsonPath("$.data[1].status").value("TIGHT"));

        verify(equipmentLoadService).calculateEquipmentLoad(null);
    }

    // -------------------------------------------------------------------------
    // GET /api/equipment-load?periods=202601,202602
    // -------------------------------------------------------------------------

    @Test
    void getWithPeriods_passesPeriodsToService() throws Exception {
        List<EquipmentLoadRow> rows = List.of(
                new EquipmentLoadRow("EQ-01", "CNC", 202601, 50.0, 231.0, 0.22, "LOOSE"));
        when(equipmentLoadService.calculateEquipmentLoad(List.of(202601, 202602))).thenReturn(rows);

        mockMvc.perform(get("/api/equipment-load")
                        .param("periods", "202601", "202602"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(equipmentLoadService).calculateEquipmentLoad(List.of(202601, 202602));
    }

    // -------------------------------------------------------------------------
    // 响应字段验证
    // -------------------------------------------------------------------------

    @Test
    void responseIncludesAllFields() throws Exception {
        List<EquipmentLoadRow> rows = List.of(
                new EquipmentLoadRow("EQ-01", "CNC", 202601, 115.5, 231.0, 0.5, "NORMAL"));
        when(equipmentLoadService.calculateEquipmentLoad(any())).thenReturn(rows);

        mockMvc.perform(get("/api/equipment-load"))
                .andExpect(jsonPath("$.data[0].equipment").value("EQ-01"))
                .andExpect(jsonPath("$.data[0].process").value("CNC"))
                .andExpect(jsonPath("$.data[0].yearMonth").value(202601))
                .andExpect(jsonPath("$.data[0].taskTimeHours").value(115.5))
                .andExpect(jsonPath("$.data[0].availableTimeHours").value(231.0))
                .andExpect(jsonPath("$.data[0].utilizationRate").value(0.5))
                .andExpect(jsonPath("$.data[0].status").value("NORMAL"));
    }

    // -------------------------------------------------------------------------
    // 空结果
    // -------------------------------------------------------------------------

    @Test
    void noData_returnsEmptyArray() throws Exception {
        when(equipmentLoadService.calculateEquipmentLoad(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/equipment-load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
