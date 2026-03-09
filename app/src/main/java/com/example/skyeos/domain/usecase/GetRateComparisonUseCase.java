package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.RateComparisonSummary;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class GetRateComparisonUseCase {
    private final LifeOsMetricsRepository repository;

    public GetRateComparisonUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public RateComparisonSummary execute(String anchorDate, String windowType) {
        return repository.getRateComparison(anchorDate, windowType);
    }
}
