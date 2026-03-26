package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class UpsertMonthlyCostBaselineUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public UpsertMonthlyCostBaselineUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String month, long basicLivingCents, long fixedSubscriptionCents) {
        repository.upsertMonthlyBaseline(month, basicLivingCents, fixedSubscriptionCents);
    }
}
