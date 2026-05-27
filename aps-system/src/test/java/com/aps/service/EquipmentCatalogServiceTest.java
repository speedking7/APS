package com.aps.service;

import com.aps.entity.EquipmentCatalog;
import com.aps.repository.EquipmentCatalogRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentCatalogServiceTest {

    @InjectMocks
    private EquipmentCatalogService service;

    @Mock
    private EquipmentCatalogRepository repository;

    @Test
    void save_rejectsDuplicateDepartmentAndEquipmentModel() {
        when(repository.existsByManufacturingDepartmentAndEquipmentModel("制造一部", "aa001"))
                .thenReturn(true);

        EquipmentCatalog entity = new EquipmentCatalog(
                null, "制造一部", "冲压设备", "AIDA", "aa001", 3);

        assertThatThrownBy(() -> service.save(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("制造部门+设备小类已存在");
    }

    @Test
    void replaceByDepartments_replacesOnlyTouchedDepartments() {
        List<EquipmentCatalog> imported = List.of(
                new EquipmentCatalog(null, "制造一部", "冲压设备", "AIDA", "aa001", 4),
                new EquipmentCatalog(null, "制造一部", "冲压设备", "AIDA", "aa002", 2)
        );

        service.replaceByDepartments(imported);

        verify(repository).deleteByManufacturingDepartmentIn(Set.of("制造一部"));
        verify(repository).saveAll(imported);
    }

    @Test
    void exportWorkbook_writesInstructionRowSheetNameAndHeaders() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new EquipmentCatalog(1L, "制造一部", "冲压设备", "AIDA", "aa001", 4)
        ));

        byte[] bytes = service.exportWorkbook();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("设备清单");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .contains("按制造部门全删全导");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("制造部门");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("台数");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("aa001");
        }
    }
}
