package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class SetCurrentMonthBasicLivingCostUseCase {
    private final LifeOsMetricsRepository repository;

    public SetCurrentMonthBasicLivingCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(long cents) {
        repository.setCurrentMonthBasicLivingCents(cents);
    }
}
