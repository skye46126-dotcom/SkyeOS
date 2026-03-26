package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateLearningUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public CreateLearningUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(CreateLearningInput input) {
        return repository.createLearning(input);
    }
}

