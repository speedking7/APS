package com.aps.service;

import com.aps.dto.ProductionPlanView;
import com.aps.entity.PartMaster;
import com.aps.entity.ProductionPlan;
import com.aps.repository.PartMasterRepository;
import com.aps.repository.ProductionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPlanServiceTest {

    @InjectMocks
    private ProductionPlanService service;

    @Mock
    private ProductionPlanRepository repository;

    @Mock
    private PartMasterRepository partMasterRepository;

    @Test
    void findByVersion_enrichesItemAndFinishedProductAttributes() {
        ProductionPlan plan = new ProductionPlan();
        plan.setFinishedProductCode("FP001");
        plan.setItemCode("C001");
        plan.setVersion("v1");

        when(repository.findByVersion("v1")).thenReturn(List.of(plan));
        when(partMasterRepository.findByPartNoIn(Set.of("FP001", "C001")))
                .thenReturn(List.of(
                        new PartMaster(1L, "FP001", "整机", "FNO-1", "项目A"),
                        new PartMaster(2L, "C001", "支架", "CNO-1", "项目A")));

        List<ProductionPlanView> views = service.findViewsByVersion("v1");

        assertThat(views).hasSize(1);
        ProductionPlanView view = views.get(0);
        assertThat(view.getItemProductName()).isEqualTo("支架");
        assertThat(view.getItemProductNo()).isEqualTo("CNO-1");
        assertThat(view.getFinishedProductName()).isEqualTo("整机");
        assertThat(view.getFinishedProductNo()).isEqualTo("FNO-1");
    }

    @Test
    void findByVersion_returnsNullExtendedFieldsWhenPartMasterMissing() {
        ProductionPlan plan = new ProductionPlan();
        plan.setFinishedProductCode("FP001");
        plan.setItemCode("C001");
        plan.setVersion("v1");

        when(repository.findByVersion("v1")).thenReturn(List.of(plan));
        when(partMasterRepository.findByPartNoIn(Set.of("FP001", "C001")))
                .thenReturn(Collections.emptyList());

        ProductionPlanView view = service.findViewsByVersion("v1").get(0);

        assertThat(view.getItemProductName()).isNull();
        assertThat(view.getFinishedProductName()).isNull();
    }
}
