package com.aps.controller;

import com.aps.entity.PartMaster;
import com.aps.service.PartMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartMasterController.class)
class PartMasterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PartMasterService partMasterService;

    @Test
    void create_and_queryByPartNo_returnPartMaster() throws Exception {
        PartMaster saved = new PartMaster(1L, "P001", "前保险杠", "PN-001", "A项目");
        when(partMasterService.save(any())).thenReturn(saved);
        when(partMasterService.findByPartNo("P001")).thenReturn(saved);

        mockMvc.perform(post("/api/part-master")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partNo\":\"P001\",\"productName\":\"前保险杠\",\"productNo\":\"PN-001\",\"projectName\":\"A项目\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.partNo").value("P001"));

        mockMvc.perform(get("/api/part-master/by-part-no/P001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.productName").value("前保险杠"));
    }

    @Test
    void getAll_update_delete_work() throws Exception {
        PartMaster saved = new PartMaster(1L, "P001", "前保险杠", "PN-001", "A项目");
        when(partMasterService.findAll()).thenReturn(List.of(saved));
        when(partMasterService.findById(1L)).thenReturn(saved);
        when(partMasterService.update(any(), any())).thenReturn(saved);
        doNothing().when(partMasterService).delete(1L);

        mockMvc.perform(get("/api/part-master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].partNo").value("P001"));

        mockMvc.perform(get("/api/part-master/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productNo").value("PN-001"));

        mockMvc.perform(put("/api/part-master/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partNo\":\"P001\",\"productName\":\"前保险杠\",\"productNo\":\"PN-001\",\"projectName\":\"A项目\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectName").value("A项目"));

        mockMvc.perform(delete("/api/part-master/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void batchUpsert_returnsSavedList() throws Exception {
        PartMaster first = new PartMaster(1L, "P001", "前保险杠", "PN-001", "A项目");
        PartMaster second = new PartMaster(2L, "P002", "后保险杠", "PN-002", "B项目");
        when(partMasterService.saveAllUpsert(any())).thenReturn(List.of(first, second));

        mockMvc.perform(post("/api/part-master/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"partNo\":\"P001\",\"productName\":\"前保险杠\",\"productNo\":\"PN-001\",\"projectName\":\"A项目\"}," +
                                "{\"partNo\":\"P002\",\"productName\":\"后保险杠\",\"productNo\":\"PN-002\",\"projectName\":\"B项目\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].partNo").value("P001"))
                .andExpect(jsonPath("$.data[1].partNo").value("P002"));
    }
}
