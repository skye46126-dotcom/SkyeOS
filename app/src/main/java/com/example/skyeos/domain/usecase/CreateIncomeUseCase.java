package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateIncomeUseCase {
    private final LifeOsWriteRepository repository;

    public CreateIncomeUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateIncomeInput input) {
        return repository.createIncome(input);
    }
}

