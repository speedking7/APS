package com.aps.controller;

import com.aps.dto.ProductionPlanView;
import com.aps.service.PlanCalculationService;
import com.aps.service.ProductionPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductionPlanController.class)
class ProductionPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductionPlanService productionPlanService;

    @MockBean
    private PlanCalculationService planCalculationService;

    private ProductionPlanView makePlan(Long id, String product, String item, int period, double qty) {
        ProductionPlanView p = new ProductionPlanView();
        p.setId(id);
        p.setFinishedProductCode(product);
        p.setItemCode(item);
        p.setYearMonth(period);
        p.setPlanQty(qty);
        p.setIsProduce("Y");
        return p;
    }

    // -------------------------------------------------------------------------
    // POST /api/production-plan/calculate
    // -------------------------------------------------------------------------

    @Test
    void calculate_triggersServiceAndReturnsSuccess() throws Exception {
        doNothing().when(planCalculationService).calculate(any());

        mockMvc.perform(post("/api/production-plan/calculate")
                        .contentType("application/json")
                        .content("{\"version\":\"20260331-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("calculation completed"));

        verify(planCalculationService).calculate(any());
    }

    // -------------------------------------------------------------------------
    // GET /api/production-plan
    // -------------------------------------------------------------------------

    @Test
    void getAll_returnsAllPlans() throws Exception {
        List<ProductionPlanView> plans = List.of(
                makePlan(1L, "P001", "P001", 202601, 146.46),
                makePlan(2L, "P001", "C001", 202601, 292.92));
        when(productionPlanService.findAllViews()).thenReturn(plans);

        mockMvc.perform(get("/api/production-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // -------------------------------------------------------------------------
    // GET /api/production-plan/by-period/{yearMonth}
    // -------------------------------------------------------------------------

    @Test
    void getByPeriod_returnsMatchingPlans() throws Exception {
        List<ProductionPlanView> plans = List.of(makePlan(1L, "P001", "P001", 202601, 146.46));
        when(productionPlanService.findViewsByYearMonth(202601)).thenReturn(plans);

        mockMvc.perform(get("/api/production-plan/by-period/202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].yearMonth").value(202601));
    }

    @Test
    void getByPeriod_noResults_returnsEmptyArray() throws Exception {
        when(productionPlanService.findViewsByYearMonth(202699)).thenReturn(List.of());

        mockMvc.perform(get("/api/production-plan/by-period/202699"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // -------------------------------------------------------------------------
    // GET /api/production-plan/by-product/{code}
    // -------------------------------------------------------------------------

    @Test
    void getByProduct_returnsAllPeriodsForProduct() throws Exception {
        List<ProductionPlanView> plans = List.of(
                makePlan(1L, "P001", "P001", 202601, 146.46),
                makePlan(2L, "P001", "P001", 202602, 140.40));
        when(productionPlanService.findViewsByFinishedProductCode("P001")).thenReturn(plans);

        mockMvc.perform(get("/api/production-plan/by-product/P001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].finishedProductCode").value("P001"));
    }

    // -------------------------------------------------------------------------
    // GET /api/production-plan/by-product/{code}/period/{yearMonth}
    // -------------------------------------------------------------------------

    @Test
    void getByProductAndPeriod_returnsFilteredResult() throws Exception {
        List<ProductionPlanView> plans = List.of(
                makePlan(1L, "P001", "P001", 202601, 146.46),
                makePlan(2L, "P001", "C001", 202601, 292.92));
        when(productionPlanService.findViewsByFinishedProductCodeAndYearMonth("P001", 202601))
                .thenReturn(plans);

        mockMvc.perform(get("/api/production-plan/by-product/P001/period/202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].finishedProductCode", everyItem(equalTo("P001"))))
                .andExpect(jsonPath("$.data[*].yearMonth", everyItem(equalTo(202601))));
    }

    @Test
    void getByVersion_returnsEnrichedFields() throws Exception {
        ProductionPlanView plan = makePlan(1L, "P001", "C001", 202601, 146.46);
        plan.setItemProductName("支架");
        plan.setFinishedProductName("总成A");
        when(productionPlanService.findViewsByVersion("v1")).thenReturn(List.of(plan));

        mockMvc.perform(get("/api/production-plan/by-version/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemProductName").value("支架"))
                .andExpect(jsonPath("$.data[0].finishedProductName").value("总成A"));
    }
}
