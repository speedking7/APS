package com.aps.controller;

import com.aps.entity.SharedMoldRule;
import com.aps.service.ImportResult;
import com.aps.service.SharedMoldRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SharedMoldRuleController.class)
class SharedMoldRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SharedMoldRuleService service;

    @Test
    void create_and_findAll_return_rules() throws Exception {
        SharedMoldRule saved = new SharedMoldRule(1L, "203000324D", "203000326D", "CX008-15", "M-01", true, "共模测试");
        when(service.save(any())).thenReturn(saved);
        when(service.findAll()).thenReturn(List.of(saved));

        mockMvc.perform(post("/api/shared-mold-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productACode\":\"203000324D\",\"productBCode\":\"203000326D\",\"equipmentCode\":\"CX008-15\",\"moldCode\":\"M-01\",\"enabled\":true,\"remark\":\"共模测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productACode").value("203000324D"));

        mockMvc.perform(get("/api/shared-mold-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productBCode").value("203000326D"));
    }

    @Test
    void import_and_export_work() throws Exception {
        ImportResult result = new ImportResult();
        result.setSkippedCount(1);
        when(service.importWorkbook(any())).thenReturn(result);
        when(service.exportWorkbook()).thenReturn(new byte[]{1, 2, 3});

        MockMultipartFile file = new MockMultipartFile(
                "file", "shared-mold-rules.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/shared-mold-rules/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skippedCount").value(1));

        mockMvc.perform(get("/api/shared-mold-rules/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("template-shared-mold-rules.xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
