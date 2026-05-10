package com.aps.controller;

import com.aps.service.ExcelImportService;
import com.aps.service.ImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExcelImportController.class)
class ExcelImportControllerTest {

    @Autowired  private MockMvc mockMvc;
    @MockBean   private ExcelImportService excelImportService;

    @Test
    void upload_returnsImportResult() throws Exception {
        ImportResult r = new ImportResult();
        r.setForecastCount(3);
        r.setScrapRateCount(6);
        r.setBomCount(5);
        when(excelImportService.importFromExcel(any())).thenReturn(r);

        MockMultipartFile file = new MockMultipartFile(
                "file", "APS模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/excel/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.forecastCount").value(3))
                .andExpect(jsonPath("$.data.scrapRateCount").value(6))
                .andExpect(jsonPath("$.data.bomCount").value(5));
    }

    @Test
    void upload_noFile_returnsErrorResponse() throws Exception {
        mockMvc.perform(multipart("/api/excel/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择要导入的 Excel 文件"));
    }
}
