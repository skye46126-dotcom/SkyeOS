package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class DeleteCapexCostUseCase {
    private final LifeOsMetricsRepository repository;

    public DeleteCapexCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String id) {
        repository.deleteCapexCost(id);
    }
}
