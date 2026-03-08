package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateExpenseUseCase {
    private final LifeOsWriteRepository repository;

    public CreateExpenseUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateExpenseInput input) {
        return repository.createExpense(input);
    }
}

