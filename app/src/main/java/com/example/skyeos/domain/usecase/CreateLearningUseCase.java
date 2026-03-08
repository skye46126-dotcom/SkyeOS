package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateLearningUseCase {
    private final LifeOsWriteRepository repository;

    public CreateLearningUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateLearningInput input) {
        return repository.createLearning(input);
    }
}

