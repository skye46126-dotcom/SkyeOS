package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class SetIdealHourlyRateUseCase {
    private final LifeOsMetricsRepository repository;

    public SetIdealHourlyRateUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(long cents) {
        repository.setIdealHourlyRateCents(cents);
    }
}
