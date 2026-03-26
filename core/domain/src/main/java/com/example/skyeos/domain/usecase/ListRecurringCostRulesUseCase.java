package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.RecurringCostRuleSummary;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

import java.util.List;

public final class ListRecurringCostRulesUseCase {
    private final LifeOsMetricsRepository repository;

    @Inject
    public ListRecurringCostRulesUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public List<RecurringCostRuleSummary> execute() {
        return repository.listRecurringCostRules();
    }
}
