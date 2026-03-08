package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class GetIdealHourlyRateUseCase {
    private final LifeOsMetricsRepository repository;

    public GetIdealHourlyRateUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public long execute() {
        return repository.getIdealHourlyRateCents();
    }
}
