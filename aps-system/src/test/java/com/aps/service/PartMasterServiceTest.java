package com.aps.service;

import com.aps.entity.PartMaster;
import com.aps.repository.PartMasterRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartMasterServiceTest {

    @InjectMocks
    private PartMasterService service;

    @Mock
    private PartMasterRepository repository;

    @Test
    void save_rejectsDuplicatePartNo() {
        when(repository.existsByPartNo("P001")).thenReturn(true);

        PartMaster input = new PartMaster(null, "P001", "前保险杠", "PN-001", "A项目");

        assertThatThrownBy(() -> service.save(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partNo already exists");
    }

    @Test
    void save_update_find_work() {
        PartMaster saved = new PartMaster(1L, "P001", "前保险杠", "PN-001", "A项目");
        when(repository.existsByPartNo("P001")).thenReturn(false);
        when(repository.save(any())).thenReturn(saved);
        when(repository.findById(1L)).thenReturn(Optional.of(saved));
        when(repository.findByPartNo("P001")).thenReturn(Optional.of(saved));

        assertThat(service.save(new PartMaster(null, "P001", "前保险杠", "PN-001", "A项目")).getId()).isEqualTo(1L);
        assertThat(service.findById(1L).getPartNo()).isEqualTo("P001");
        assertThat(service.findByPartNo("P001").getProductName()).isEqualTo("前保险杠");

        service.update(1L, new PartMaster(null, "P001", "前保险杠改", "PN-001", "A项目"));
        assertThat(saved.getProductName()).isEqualTo("前保险杠改");
        verify(repository, times(2)).save(any());
    }

    @Test
    void saveAllUpsert_updatesExistingAndCreatesMissing() {
        PartMaster existing = new PartMaster(1L, "P001", "旧名称", "OLD-001", "旧项目");
        PartMaster newItem = new PartMaster(null, "P002", "后保险杠", "PN-002", "B项目");
        when(repository.findByPartNoIn(List.of("P001", "P002"))).thenReturn(List.of(existing));
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PartMaster> result = service.saveAllUpsert(List.of(
                new PartMaster(null, "P001", "前保险杠", "PN-001", "A项目"),
                newItem));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getProductName()).isEqualTo("前保险杠");
        assertThat(result.get(1).getPartNo()).isEqualTo("P002");
        verify(repository).saveAll(any());
    }

    @Test
    void exportWorkbook_writesInstructionRowSheetNameAndHeaders() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new PartMaster(1L, "P001", "前保险杠", "PN-001", "A项目")
        ));

        byte[] bytes = service.exportWorkbook();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("零件主数据");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("partNo 唯一");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("零件编码");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("项目名称");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("P001");
        }
    }

    @Test
    void importWorkbook_usesUpsertForRows() throws Exception {
        when(repository.findByPartNoIn(List.of("P001", "P002"))).thenReturn(List.of(
                new PartMaster(1L, "P001", "旧名称", "OLD-001", "旧项目")
        ));
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("零件主数据");
            sheet.createRow(0).createCell(0).setCellValue("说明");
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("零件编码");
            header.createCell(1).setCellValue("零件名称");
            header.createCell(2).setCellValue("零件番号");
            header.createCell(3).setCellValue("项目名称");
            var row1 = sheet.createRow(2);
            row1.createCell(0).setCellValue("P001");
            row1.createCell(1).setCellValue("前保险杠");
            row1.createCell(2).setCellValue("PN-001");
            row1.createCell(3).setCellValue("A项目");
            var row2 = sheet.createRow(3);
            row2.createCell(0).setCellValue("P002");
            row2.createCell(1).setCellValue("后保险杠");
            row2.createCell(2).setCellValue("PN-002");
            row2.createCell(3).setCellValue("B项目");

            var out = new java.io.ByteArrayOutputStream();
            workbook.write(out);

            ImportResult result = service.importWorkbook(new ByteArrayInputStream(out.toByteArray()));

            assertThat(result.getSkippedCount()).isEqualTo(2);
            verify(repository).saveAll(any());
        }
    }
}
