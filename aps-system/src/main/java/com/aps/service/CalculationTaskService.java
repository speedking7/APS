package com.aps.service;

import com.aps.dto.CalculateRequest;
import com.aps.dto.CalculationTaskResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CalculationTaskService {

    private final PlanCalculationService planCalculationService;
    private final TaskExecutor taskExecutor;
    private final Map<String, CalculationTaskResponse> tasks = new ConcurrentHashMap<>();

    public CalculationTaskService(
            PlanCalculationService planCalculationService,
            @Qualifier("calculationTaskExecutor") TaskExecutor taskExecutor) {
        this.planCalculationService = planCalculationService;
        this.taskExecutor = taskExecutor;
    }

    public CalculationTaskResponse submit(CalculateRequest request) {
        String taskId = UUID.randomUUID().toString();
        CalculationTaskResponse task = new CalculationTaskResponse();
        task.setTaskId(taskId);
        task.setStatus("PENDING");
        task.setVersion(request != null ? request.getVersion() : null);
        task.setResultVersion(resolveResultVersion(request));
        task.setCreatedAt(LocalDateTime.now());
        task.setMessage("任务已提交，等待执行");
        task.setProgressPercent(0);
        task.setStage("PENDING");
        tasks.put(taskId, task);

        taskExecutor.execute(() -> runTask(taskId, request));
        return copyOf(task);
    }

    public CalculationTaskResponse getTask(String taskId) {
        CalculationTaskResponse task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return copyOf(task);
    }

    private void runTask(String taskId, CalculateRequest request) {
        CalculationTaskResponse task = tasks.get(taskId);
        if (task == null) {
            return;
        }
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        task.setMessage("计算中");
        task.setProgressPercent(1);
        task.setStage("RUNNING");
        try {
            planCalculationService.calculate(request, (progressPercent, stage, currentPeriod, totalPeriods, message) -> {
                task.setProgressPercent(progressPercent);
                task.setStage(stage);
                task.setCurrentPeriod(currentPeriod);
                task.setTotalPeriods(totalPeriods);
                task.setMessage(message);
            });
            task.setStatus("SUCCEEDED");
            task.setFinishedAt(LocalDateTime.now());
            task.setMessage("计算完成");
            task.setProgressPercent(100);
            task.setStage("SUCCEEDED");
        } catch (Exception ex) {
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            task.setMessage(ex.getMessage() != null ? ex.getMessage() : "计算失败");
            task.setStage("FAILED");
        }
    }

    private String resolveResultVersion(CalculateRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getResultVersion() != null && !request.getResultVersion().isBlank()) {
            return request.getResultVersion().trim();
        }
        return request.getVersion();
    }

    private CalculationTaskResponse copyOf(CalculationTaskResponse source) {
        CalculationTaskResponse copy = new CalculationTaskResponse();
        copy.setTaskId(source.getTaskId());
        copy.setStatus(source.getStatus());
        copy.setVersion(source.getVersion());
        copy.setResultVersion(source.getResultVersion());
        copy.setMessage(source.getMessage());
        copy.setProgressPercent(source.getProgressPercent());
        copy.setStage(source.getStage());
        copy.setCurrentPeriod(source.getCurrentPeriod());
        copy.setTotalPeriods(source.getTotalPeriods());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setStartedAt(source.getStartedAt());
        copy.setFinishedAt(source.getFinishedAt());
        return copy;
    }
}
