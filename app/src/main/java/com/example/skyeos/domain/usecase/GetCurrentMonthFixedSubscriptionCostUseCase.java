package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class GetCurrentMonthFixedSubscriptionCostUseCase {
    private final LifeOsMetricsRepository repository;

    public GetCurrentMonthFixedSubscriptionCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public long execute() {
        return repository.getCurrentMonthFixedSubscriptionCents();
    }
}
