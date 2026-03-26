package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class GetMetricSnapshotUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public GetMetricSnapshotUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public MetricSnapshotSummary execute(String snapshotDate, String windowType) {
        return repository.getSnapshot(snapshotDate, windowType);
    }

    public MetricSnapshotSummary latest(String windowType) {
        return repository.getLatestSnapshot(windowType);
    }
}

