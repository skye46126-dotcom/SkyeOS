package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class DeleteCapexCostUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public DeleteCapexCostUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String id) {
        repository.deleteCapexCost(id);
    }
}
