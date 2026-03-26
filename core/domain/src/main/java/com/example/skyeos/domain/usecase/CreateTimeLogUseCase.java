package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.input.CreateTimeLogInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateTimeLogUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public CreateTimeLogUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateTimeLogInput input) {
        return repository.createTimeLog(input);
    }
}

