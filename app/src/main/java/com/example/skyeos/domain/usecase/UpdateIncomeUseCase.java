package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class UpdateIncomeUseCase {
    private final LifeOsWriteRepository repository;

    public UpdateIncomeUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String incomeId, CreateIncomeInput input) {
        repository.updateIncome(incomeId, input);
    }
}
