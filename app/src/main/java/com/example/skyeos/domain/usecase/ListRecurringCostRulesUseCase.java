package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.RecurringCostRuleSummary;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

import java.util.List;

public final class ListRecurringCostRulesUseCase {
    private final LifeOsMetricsRepository repository;

    public ListRecurringCostRulesUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public List<RecurringCostRuleSummary> execute() {
        return repository.listRecurringCostRules();
    }
}
