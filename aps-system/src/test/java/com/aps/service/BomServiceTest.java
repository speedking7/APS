package com.aps.service;

import com.aps.entity.Bom;
import com.aps.repository.BomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomServiceTest {

    @InjectMocks
    private BomService service;

    @Mock
    private BomRepository repository;

    @Captor
    private ArgumentCaptor<Bom> bomCaptor;

    @Test
    void save_keepsPartAttribute() {
        Bom entity = new Bom();
        entity.setRootProductCode("FP100");
        entity.setParentCode("FP100");
        entity.setChildCode("CP100");
        entity.setPartAttribute("采购件");

        service.save(entity);

        verify(repository).save(bomCaptor.capture());
        assertThat(bomCaptor.getValue().getPartAttribute()).isEqualTo("采购件");
    }

    @Test
    void update_copiesPartAttributeToExistingEntity() {
        Bom existing = new Bom();
        existing.setId(1L);
        existing.setRootProductCode("FP100");
        existing.setParentCode("FP100");
        existing.setPartAttribute("旧属性");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        Bom update = new Bom();
        update.setRootProductCode("FP100");
        update.setParentCode("FP100");
        update.setPartAttribute("自制件");

        service.update(1L, update);

        verify(repository).save(bomCaptor.capture());
        assertThat(bomCaptor.getValue().getPartAttribute()).isEqualTo("自制件");
    }
}
