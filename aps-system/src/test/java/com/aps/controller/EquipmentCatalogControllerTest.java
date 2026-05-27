package com.aps.controller;

import com.aps.entity.EquipmentCatalog;
import com.aps.service.EquipmentCatalogService;
import com.aps.service.ImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
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

@WebMvcTest(EquipmentCatalogController.class)
class EquipmentCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EquipmentCatalogService service;

    @Test
    void create_and_findAll_returnCatalogRows() throws Exception {
        EquipmentCatalog saved = new EquipmentCatalog(1L, "制造一部", "冲压设备", "AIDA", "aa001", 4);
        when(service.save(any())).thenReturn(saved);
        when(service.findAll()).thenReturn(List.of(saved));

        mockMvc.perform(post("/api/equipment-catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manufacturingDepartment\":\"制造一部\",\"equipmentCategory\":\"冲压设备\",\"equipmentBrand\":\"AIDA\",\"equipmentModel\":\"aa001\",\"equipmentCount\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipmentModel").value("aa001"));

        mockMvc.perform(get("/api/equipment-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].equipmentBrand").value("AIDA"));
    }

    @Test
    void importWorkbook_returnsImportedCount() throws Exception {
        ImportResult result = new ImportResult();
        result.setSkippedCount(2);
        when(service.importWorkbook(any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
                "file", "equipment.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/equipment-catalog/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skippedCount").value(2));
    }

    @Test
    void exportWorkbook_returnsExcelAttachment() throws Exception {
        when(service.exportWorkbook()).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/equipment-catalog/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("template-equipment-catalog.xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
