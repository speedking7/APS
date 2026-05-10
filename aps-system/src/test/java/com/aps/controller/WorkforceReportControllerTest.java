package com.aps.controller;

import com.aps.service.WorkforceReportService;
import com.aps.service.WorkforceRow;
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

@WebMvcTest(WorkforceReportController.class)
class WorkforceReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkforceReportService workforceReportService;

    // -------------------------------------------------------------------------
    // GET /api/workforce-report  （无 period 参数 → 全量查询）
    // -------------------------------------------------------------------------

    @Test
    void getAll_noParams_returnsAllRows() throws Exception {
        List<WorkforceRow> rows = List.of(
                new WorkforceRow("CNC", "EQ-01", 202601, 200.0),
                new WorkforceRow("WELD", "EQ-02", 202601, 150.0));
        when(workforceReportService.calculateWorkforceReport(null)).thenReturn(rows);

        mockMvc.perform(get("/api/workforce-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].process").value("CNC"))
                .andExpect(jsonPath("$.data[0].workforceDemand").value(200.0));

        verify(workforceReportService).calculateWorkforceReport(null);
    }

    // -------------------------------------------------------------------------
    // GET /api/workforce-report?periods=202601,202602
    // -------------------------------------------------------------------------

    @Test
    void getWithPeriods_passesPeriodsToService() throws Exception {
        List<WorkforceRow> rows = List.of(new WorkforceRow("CNC", "EQ-01", 202601, 200.0));
        when(workforceReportService.calculateWorkforceReport(List.of(202601, 202602))).thenReturn(rows);

        mockMvc.perform(get("/api/workforce-report")
                        .param("periods", "202601", "202602"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(workforceReportService).calculateWorkforceReport(List.of(202601, 202602));
    }

    // -------------------------------------------------------------------------
    // 空结果
    // -------------------------------------------------------------------------

    @Test
    void getAll_noData_returnsEmptyArray() throws Exception {
        when(workforceReportService.calculateWorkforceReport(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/workforce-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
