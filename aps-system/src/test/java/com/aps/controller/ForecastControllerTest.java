package com.aps.controller;

import com.aps.entity.Forecast;
import com.aps.service.ForecastService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ForecastController.class)
class ForecastControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ForecastService forecastService;

    // -------------------------------------------------------------------------
    // GET /api/forecast
    // -------------------------------------------------------------------------

    @Test
    void getAll_returnsSuccessWithList() throws Exception {
        List<Forecast> forecasts = List.of(
                new Forecast(1L, "P001", 202601, 100.0, null),
                new Forecast(2L, "P002", 202601, 200.0, null));
        when(forecastService.findAll()).thenReturn(forecasts);

        mockMvc.perform(get("/api/forecast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].itemCode").value("P001"))
                .andExpect(jsonPath("$.data[1].itemCode").value("P002"));
    }

    @Test
    void getAll_emptyList_returnsSuccessWithEmptyArray() throws Exception {
        when(forecastService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/forecast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // -------------------------------------------------------------------------
    // GET /api/forecast/{id}
    // -------------------------------------------------------------------------

    @Test
    void getById_found_returnsEntity() throws Exception {
        Forecast fc = new Forecast(1L, "P001", 202601, 100.0, "分类1");
        when(forecastService.findById(1L)).thenReturn(fc);

        mockMvc.perform(get("/api/forecast/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.itemCode").value("P001"))
                .andExpect(jsonPath("$.data.yearMonth").value(202601))
                .andExpect(jsonPath("$.data.quantity").value(100.0));
    }

    @Test
    void getById_notFound_returns400WithErrorMessage() throws Exception {
        when(forecastService.findById(999L))
                .thenThrow(new IllegalArgumentException("Forecast not found: 999"));

        mockMvc.perform(get("/api/forecast/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Forecast not found: 999"));
    }

    // -------------------------------------------------------------------------
    // POST /api/forecast
    // -------------------------------------------------------------------------

    @Test
    void create_validEntity_savesAndReturnsWithId() throws Exception {
        Forecast input = new Forecast(null, "P001", 202601, 100.0, null);
        Forecast saved = new Forecast(1L, "P001", 202601, 100.0, null);
        when(forecastService.save(any(Forecast.class))).thenReturn(saved);

        mockMvc.perform(post("/api/forecast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.itemCode").value("P001"));

        verify(forecastService).save(any(Forecast.class));
    }

    // -------------------------------------------------------------------------
    // PUT /api/forecast/{id}
    // -------------------------------------------------------------------------

    @Test
    void update_existingEntity_returnsUpdated() throws Exception {
        Forecast updated = new Forecast(1L, "P001", 202602, 150.0, null);
        when(forecastService.update(eq(1L), any(Forecast.class))).thenReturn(updated);

        mockMvc.perform(put("/api/forecast/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.yearMonth").value(202602))
                .andExpect(jsonPath("$.data.quantity").value(150.0));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/forecast/{id}
    // -------------------------------------------------------------------------

    @Test
    void delete_existingId_returns200() throws Exception {
        doNothing().when(forecastService).delete(1L);

        mockMvc.perform(delete("/api/forecast/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(forecastService).delete(1L);
    }

    // -------------------------------------------------------------------------
    // POST /api/forecast/batch
    // -------------------------------------------------------------------------

    @Test
    void batchSave_multipleEntities_returnsAllWithIds() throws Exception {
        List<Forecast> input = List.of(
                new Forecast(null, "P001", 202601, 100.0, null),
                new Forecast(null, "P002", 202601, 200.0, null));
        List<Forecast> saved = List.of(
                new Forecast(1L, "P001", 202601, 100.0, null),
                new Forecast(2L, "P002", 202601, 200.0, null));
        when(forecastService.saveAll(any())).thenReturn(saved);

        mockMvc.perform(post("/api/forecast/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));
    }
}
