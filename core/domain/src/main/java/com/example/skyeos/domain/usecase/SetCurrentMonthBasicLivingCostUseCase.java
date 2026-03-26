package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class SetCurrentMonthBasicLivingCostUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public SetCurrentMonthBasicLivingCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(long cents) {
        repository.setCurrentMonthBasicLivingCents(cents);
    }
}
