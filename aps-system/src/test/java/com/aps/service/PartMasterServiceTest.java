package com.aps.service;

import com.aps.entity.PartMaster;
import com.aps.repository.PartMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
