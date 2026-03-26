package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.MonthlyCostBaseline;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class GetMonthlyCostBaselineUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public GetMonthlyCostBaselineUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public MonthlyCostBaseline execute(String month) {
        return repository.getMonthlyBaseline(month);
    }
}
