package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class GetCurrentMonthBasicLivingCostUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public GetCurrentMonthBasicLivingCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public long execute() {
        return repository.getCurrentMonthBasicLivingCents();
    }
}
