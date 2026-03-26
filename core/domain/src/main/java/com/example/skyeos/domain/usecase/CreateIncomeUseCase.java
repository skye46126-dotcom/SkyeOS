package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateIncomeUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public CreateIncomeUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateIncomeInput input) {
        return repository.createIncome(input);
    }
}

