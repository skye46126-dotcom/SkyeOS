package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class RecomputeMetricSnapshotUseCase {
    private final LifeOsMetricsRepository repository;

    public RecomputeMetricSnapshotUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public MetricSnapshotSummary execute(String snapshotDate, String windowType) {
        return repository.recomputeSnapshot(snapshotDate, windowType);
    }
}

