package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class UpdateExpenseUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public UpdateExpenseUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String expenseId, CreateExpenseInput input) {
        repository.updateExpense(expenseId, input);
    }
}
