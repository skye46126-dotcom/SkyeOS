package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class CreateCapexCostUseCase {
    private final LifeOsMetricsRepository repository;

    public CreateCapexCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String name, String purchaseDate, long purchaseAmountCents, int usefulMonths,
            int residualRateBps, String note) {
        repository.createCapexCost(name, purchaseDate, purchaseAmountCents, usefulMonths, residualRateBps, note);
    }
}
