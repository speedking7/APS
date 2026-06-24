package com.aps.service;

import com.aps.dto.CalculateRequest;
import com.aps.dto.CalculationTaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CalculationTaskServiceTest {

    @Mock
    private PlanCalculationService planCalculationService;

    private CalculationTaskService service;

    @BeforeEach
    void setUp() {
        TaskExecutor directExecutor = Runnable::run;
        service = new CalculationTaskService(planCalculationService, directExecutor);
    }

    @Test
    void submit_runsTaskAndMarksSucceeded() {
        doNothing().when(planCalculationService).calculate(any(), any());

        CalculationTaskResponse submitted = service.submit(request("v1", "r1"));
        CalculationTaskResponse status = service.getTask(submitted.getTaskId());

        assertThat(submitted.getTaskId()).isNotBlank();
        assertThat(submitted.getProgressPercent()).isBetween(0, 100);
        assertThat(submitted.getStage()).isIn("PENDING", "RUNNING", "SUCCEEDED");
        assertThat(status.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(status.getProgressPercent()).isEqualTo(100);
        assertThat(status.getStage()).isEqualTo("SUCCEEDED");
        assertThat(status.getResultVersion()).isEqualTo("r1");
    }

    @Test
    void submit_whenCalculationFails_marksTaskFailed() {
        doThrow(new IllegalStateException("boom")).when(planCalculationService).calculate(any(), any());

        CalculationTaskResponse submitted = service.submit(request("v1", "r1"));
        CalculationTaskResponse status = service.getTask(submitted.getTaskId());

        assertThat(status.getStatus()).isEqualTo("FAILED");
        assertThat(status.getStage()).isEqualTo("FAILED");
        assertThat(status.getMessage()).contains("boom");
    }

    private CalculateRequest request(String version, String resultVersion) {
        CalculateRequest req = new CalculateRequest();
        req.setVersion(version);
        req.setResultVersion(resultVersion);
        return req;
    }
}
