package com.aps.controller;

import com.aps.service.WorkforceDetailRow;
import com.aps.service.WorkforceDetailService;
import com.aps.service.WorkforceReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkforceReportController.class)
class WorkforceDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkforceReportService workforceReportService;

    @MockBean
    private WorkforceDetailService workforceDetailService;

    private WorkforceDetailRow makeRow() {
        WorkforceDetailRow row = new WorkforceDetailRow();
        row.setManufacturingDepartment("制造一部");
        row.setManufacturingUnit("单元A");
        row.setProject("项目A");
        row.setProductName("支架");
        row.setProductNo("NO-001");
        row.setProductCode("P-100");
        row.setYearMonth(202601);
        row.setPlanQty(120.0);
        row.setProcess("冲压");
        row.setStaffCount(2.0);
        row.setTaktTime(30.0);
        row.setRequiredSeconds(7200.0);
        row.setRequiredHours(2.0);
        return row;
    }

    @Test
    void getDetails_withVersion_returnsDetailRows() throws Exception {
        when(workforceDetailService.findDetailsByVersion("v1")).thenReturn(List.of(makeRow()));

        mockMvc.perform(get("/api/workforce-report/details").param("version", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].manufacturingDepartment").value("制造一部"))
                .andExpect(jsonPath("$.data[0].project").value("项目A"))
                .andExpect(jsonPath("$.data[0].staffCount").value(2.0))
                .andExpect(jsonPath("$.data[0].taktTime").value(30.0))
                .andExpect(jsonPath("$.data[0].requiredSeconds").value(7200.0))
                .andExpect(jsonPath("$.data[0].requiredHours").value(2.0));

        verify(workforceDetailService).findDetailsByVersion("v1");
    }

    @Test
    void getDetails_withoutVersion_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/workforce-report/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("version")));
    }

    @Test
    void getDetails_emptyResult_returnsEmptyArray() throws Exception {
        when(workforceDetailService.findDetailsByVersion("v1")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/workforce-report/details").param("version", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
