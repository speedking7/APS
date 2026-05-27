package com.aps.controller;

import com.aps.service.EquipmentLoadRow;
import com.aps.service.EquipmentLoadDetailRow;
import com.aps.service.EquipmentLoadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EquipmentLoadController.class)
class EquipmentLoadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EquipmentLoadService equipmentLoadService;

    private EquipmentLoadRow makeRow() {
        EquipmentLoadRow row = new EquipmentLoadRow();
        row.setManufacturingDepartment("制造一部");
        row.setEquipmentCategory("冲压设备");
        row.setEquipmentBrand("AIDA");
        row.setEquipmentModel("aa001");
        row.setEquipmentCount(4);
        row.setYearMonth(202601);
        row.setWorkDays(22.0);
        row.setPlanQty(3600.0);
        row.setCycleTime(10.0);
        row.setMoldCavity(2);
        row.setRequiredSeconds(18000.0);
        row.setDailyHours(10.5);
        row.setAvailableSecondsPerMachine(831600.0);
        row.setAvailableSecondsTotal(3326400.0);
        row.setRequiredMachineCount(0.0216450216);
        row.setDifference(3.9783549784);
        row.setLoadRate(0.0054112554);
        row.setMatchedCatalog(true);
        row.setSharedMoldAdjusted(true);
        row.setSharedMoldSuppressed(false);
        row.setSharedMoldGroupKey("203000324D|203000326D");
        EquipmentLoadDetailRow detail = new EquipmentLoadDetailRow();
        detail.setItemCode("203000326D");
        detail.setFinishedProductCode("100400040D");
        detail.setYearMonth(202601);
        detail.setProcess("冲压");
        detail.setEquipment("aa001");
        detail.setMoldCavity(2);
        detail.setPlanQty(3600.0);
        detail.setCycleTime(10.0);
        detail.setRequiredSecondsRaw(18000.0);
        detail.setRequiredSecondsEffective(18000.0);
        detail.setSharedMoldAdjusted(true);
        detail.setSharedMoldSuppressed(false);
        detail.setSharedMoldGroupKey("203000324D|203000326D");
        detail.setSharedMoldPeerItemCode(null);
        row.setDetailRows(List.of(detail));

        row.setEquipment("aa001");
        row.setProcess("冲压");
        row.setTaskTimeHours(5.0);
        row.setAvailableTimeHours(231.0);
        row.setUtilizationRate(0.0054112554);
        row.setStatus("LOOSE");
        return row;
    }

    @Test
    void getAll_noParams_returnsAllRows() throws Exception {
        EquipmentLoadRow row1 = makeRow();
        EquipmentLoadRow row2 = makeRow();
        row2.setEquipmentModel("aa002");
        row2.setMatchedCatalog(false);
        when(equipmentLoadService.calculateEquipmentLoad(null, null)).thenReturn(List.of(row1, row2));

        mockMvc.perform(get("/api/equipment-load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].equipmentModel").value("aa001"))
                .andExpect(jsonPath("$.data[0].status").value("LOOSE"))
                .andExpect(jsonPath("$.data[1].matchedCatalog").value(false));

        verify(equipmentLoadService).calculateEquipmentLoad(null, null);
    }

    @Test
    void getWithPeriods_passesPeriodsToService() throws Exception {
        when(equipmentLoadService.calculateEquipmentLoad(List.of(202601, 202602), null))
                .thenReturn(List.of(makeRow()));

        mockMvc.perform(get("/api/equipment-load")
                        .param("periods", "202601", "202602"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(equipmentLoadService).calculateEquipmentLoad(List.of(202601, 202602), null);
    }

    @Test
    void getWithVersion_passesVersionToService() throws Exception {
        when(equipmentLoadService.calculateEquipmentLoad(null, "v1")).thenReturn(List.of(makeRow()));

        mockMvc.perform(get("/api/equipment-load")
                        .param("version", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(equipmentLoadService).calculateEquipmentLoad(null, "v1");
    }

    @Test
    void responseIncludesAllFields() throws Exception {
        when(equipmentLoadService.calculateEquipmentLoad(any(), any())).thenReturn(List.of(makeRow()));

        mockMvc.perform(get("/api/equipment-load"))
                .andExpect(jsonPath("$.data[0].manufacturingDepartment").value("制造一部"))
                .andExpect(jsonPath("$.data[0].equipmentCategory").value("冲压设备"))
                .andExpect(jsonPath("$.data[0].equipmentBrand").value("AIDA"))
                .andExpect(jsonPath("$.data[0].equipmentModel").value("aa001"))
                .andExpect(jsonPath("$.data[0].equipmentCount").value(4))
                .andExpect(jsonPath("$.data[0].yearMonth").value(202601))
                .andExpect(jsonPath("$.data[0].workDays").value(22.0))
                .andExpect(jsonPath("$.data[0].planQty").value(3600.0))
                .andExpect(jsonPath("$.data[0].cycleTime").value(10.0))
                .andExpect(jsonPath("$.data[0].moldCavity").value(2))
                .andExpect(jsonPath("$.data[0].requiredSeconds").value(18000.0))
                .andExpect(jsonPath("$.data[0].dailyHours").value(10.5))
                .andExpect(jsonPath("$.data[0].availableSecondsPerMachine").value(831600.0))
                .andExpect(jsonPath("$.data[0].availableSecondsTotal").value(3326400.0))
                .andExpect(jsonPath("$.data[0].requiredMachineCount").value(0.0216450216))
                .andExpect(jsonPath("$.data[0].difference").value(3.9783549784))
                .andExpect(jsonPath("$.data[0].loadRate").value(0.0054112554))
                .andExpect(jsonPath("$.data[0].matchedCatalog").value(true))
                .andExpect(jsonPath("$.data[0].sharedMoldAdjusted").value(true))
                .andExpect(jsonPath("$.data[0].sharedMoldGroupKey").value("203000324D|203000326D"))
                .andExpect(jsonPath("$.data[0].detailRows", hasSize(1)))
                .andExpect(jsonPath("$.data[0].detailRows[0].itemCode").value("203000326D"))
                .andExpect(jsonPath("$.data[0].detailRows[0].requiredSecondsEffective").value(18000.0))
                .andExpect(jsonPath("$.data[0].taskTimeHours").value(5.0))
                .andExpect(jsonPath("$.data[0].availableTimeHours").value(231.0))
                .andExpect(jsonPath("$.data[0].utilizationRate").value(0.0054112554))
                .andExpect(jsonPath("$.data[0].status").value("LOOSE"));
    }

    @Test
    void noData_returnsEmptyArray() throws Exception {
        when(equipmentLoadService.calculateEquipmentLoad(any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/equipment-load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
