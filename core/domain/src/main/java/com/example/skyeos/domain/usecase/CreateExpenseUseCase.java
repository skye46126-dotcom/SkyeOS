package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateExpenseUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public CreateExpenseUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateExpenseInput input) {
        return repository.createExpense(input);
    }
}

