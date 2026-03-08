package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class GetCurrentMonthBasicLivingCostUseCase {
    private final LifeOsMetricsRepository repository;

    public GetCurrentMonthBasicLivingCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public long execute() {
        return repository.getCurrentMonthBasicLivingCents();
    }
}
