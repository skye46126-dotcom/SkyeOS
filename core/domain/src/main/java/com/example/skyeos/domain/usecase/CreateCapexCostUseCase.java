package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class CreateCapexCostUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public CreateCapexCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String name, String purchaseDate, long purchaseAmountCents, int usefulMonths,
            int residualRateBps, String note) {
        repository.createCapexCost(name, purchaseDate, purchaseAmountCents, usefulMonths, residualRateBps, note);
    }
}
