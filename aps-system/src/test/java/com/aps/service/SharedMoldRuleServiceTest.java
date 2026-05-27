package com.aps.service;

import com.aps.entity.SharedMoldRule;
import com.aps.repository.SharedMoldRuleRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedMoldRuleServiceTest {

    @InjectMocks
    private SharedMoldRuleService service;

    @Mock
    private SharedMoldRuleRepository repository;

    @Test
    void save_rejects_duplicate_product_pair() {
        when(repository.existsByProductACodeAndProductBCode("203000324D", "203000326D"))
                .thenReturn(true);

        SharedMoldRule entity = new SharedMoldRule(
                null, "203000324D", "203000326D", null, null, true, "共模测试"
        );

        assertThatThrownBy(() -> service.save(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("产品对已存在");
    }

    @Test
    void save_normalizes_product_pair_order() {
        SharedMoldRule entity = new SharedMoldRule(
                null, "203000326D", "203000324D", null, null, true, "共模测试"
        );
        when(repository.existsByProductACodeAndProductBCode("203000324D", "203000326D"))
                .thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        SharedMoldRule saved = service.save(entity);

        assertThat(saved.getProductACode()).isEqualTo("203000324D");
        assertThat(saved.getProductBCode()).isEqualTo("203000326D");
    }

    @Test
    void exportWorkbook_writes_sheet_and_headers() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new SharedMoldRule(1L, "203000324D", "203000326D", "CX008-15", "M-01", true, "共模测试")
        ));

        byte[] bytes = service.exportWorkbook();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("共模规则");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("产品A编码");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("是否启用");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("203000326D");
        }
    }

    @Test
    void importWorkbook_saves_rows() throws Exception {
        when(repository.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("共模规则");
            sheet.createRow(0).createCell(0).setCellValue("说明");
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("产品A编码");
            header.createCell(1).setCellValue("产品B编码");
            header.createCell(2).setCellValue("设备编号");
            header.createCell(3).setCellValue("模具编号");
            header.createCell(4).setCellValue("是否启用");
            header.createCell(5).setCellValue("备注");

            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("203000324D");
            row.createCell(1).setCellValue("203000326D");
            row.createCell(2).setCellValue("CX008-15");
            row.createCell(3).setCellValue("M-01");
            row.createCell(4).setCellValue("Y");
            row.createCell(5).setCellValue("共模测试");

            var out = new java.io.ByteArrayOutputStream();
            workbook.write(out);

            ImportResult result = service.importWorkbook(new ByteArrayInputStream(out.toByteArray()));
            assertThat(result.getSkippedCount()).isEqualTo(1);
            verify(repository).deleteAll();
            verify(repository).saveAll(org.mockito.ArgumentMatchers.any());
        }
    }
}
