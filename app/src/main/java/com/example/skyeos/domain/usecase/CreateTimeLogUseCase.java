package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateTimeLogInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateTimeLogUseCase {
    private final LifeOsWriteRepository repository;

    public CreateTimeLogUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateTimeLogInput input) {
        return repository.createTimeLog(input);
    }
}

