package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class UpdateCapexCostUseCase {
    private final LifeOsMetricsRepository repository;

    public UpdateCapexCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String id, String name, String purchaseDate, long purchaseAmountCents, int usefulMonths,
            int residualRateBps, String note) {
        repository.updateCapexCost(id, name, purchaseDate, purchaseAmountCents, usefulMonths, residualRateBps, note);
    }
}
