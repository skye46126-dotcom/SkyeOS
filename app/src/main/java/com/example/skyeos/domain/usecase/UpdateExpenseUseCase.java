package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class UpdateExpenseUseCase {
    private final LifeOsWriteRepository repository;

    public UpdateExpenseUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String expenseId, CreateExpenseInput input) {
        repository.updateExpense(expenseId, input);
    }
}
