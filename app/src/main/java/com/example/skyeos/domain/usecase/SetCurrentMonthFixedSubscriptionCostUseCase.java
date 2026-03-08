package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class SetCurrentMonthFixedSubscriptionCostUseCase {
    private final LifeOsMetricsRepository repository;

    public SetCurrentMonthFixedSubscriptionCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(long cents) {
        repository.setCurrentMonthFixedSubscriptionCents(cents);
    }
}
