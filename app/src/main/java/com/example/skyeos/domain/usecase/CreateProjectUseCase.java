package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateProjectInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateProjectUseCase {
    private final LifeOsWriteRepository repository;

    public CreateProjectUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateProjectInput input) {
        return repository.createProject(input);
    }
}

