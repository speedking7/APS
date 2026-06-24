package com.aps.service;

@FunctionalInterface
public interface CalculationProgressListener {

    void onProgress(int progressPercent, String stage, Integer currentPeriod, Integer totalPeriods, String message);

    static CalculationProgressListener noop() {
        return (progressPercent, stage, currentPeriod, totalPeriods, message) -> {
        };
    }
}
