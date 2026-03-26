package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class SetCurrentMonthFixedSubscriptionCostUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public SetCurrentMonthFixedSubscriptionCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(long cents) {
        repository.setCurrentMonthFixedSubscriptionCents(cents);
    }
}
