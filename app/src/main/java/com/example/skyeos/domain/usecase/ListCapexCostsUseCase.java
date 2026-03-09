package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.CapexCostSummary;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

import java.util.List;

public final class ListCapexCostsUseCase {
    private final LifeOsMetricsRepository repository;

    public ListCapexCostsUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public List<CapexCostSummary> execute() {
        return repository.listCapexCosts();
    }
}
