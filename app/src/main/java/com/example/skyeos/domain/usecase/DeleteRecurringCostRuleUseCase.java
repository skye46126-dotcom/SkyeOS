package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class DeleteRecurringCostRuleUseCase {
    private final LifeOsMetricsRepository repository;

    public DeleteRecurringCostRuleUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String id) {
        repository.deleteRecurringCostRule(id);
    }
}
