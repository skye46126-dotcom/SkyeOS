package com.example.skyeos.domain.repository;

import com.example.skyeos.domain.model.MetricSnapshotSummary;

public interface LifeOsMetricsRepository {
    MetricSnapshotSummary recomputeSnapshot(String snapshotDate, String windowType);

    MetricSnapshotSummary getSnapshot(String snapshotDate, String windowType);

    MetricSnapshotSummary getLatestSnapshot(String windowType);

    long getIdealHourlyRateCents();

    void setIdealHourlyRateCents(long cents);
}
