package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

public final class UpdateRecurringCostRuleUseCase {
    private final LifeOsMetricsRepository repository;

    public UpdateRecurringCostRuleUseCase(LifeOsMetricsRepository repository) {
        this.repository = repository;
    }

    public void execute(String id, String name, String category, long monthlyAmountCents, boolean isNecessary,
            String startMonth, String endMonth, String note) {
        repository.updateRecurringCostRule(id, name, category, monthlyAmountCents, isNecessary, startMonth, endMonth, note);
    }
}
