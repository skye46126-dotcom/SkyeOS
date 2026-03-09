package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class CreateRecurringCostRuleUseCase {
    private final LifeOsMetricsRepository repository;

    public CreateRecurringCostRuleUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String name, String category, long monthlyAmountCents, boolean isNecessary,
            String startMonth, String endMonth, String note) {
        repository.createRecurringCostRule(name, category, monthlyAmountCents, isNecessary, startMonth, endMonth, note);
    }
}
