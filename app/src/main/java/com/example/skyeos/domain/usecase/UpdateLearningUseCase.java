package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class UpdateLearningUseCase {
    private final LifeOsWriteRepository repository;

    public UpdateLearningUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String learningId, CreateLearningInput input) {
        repository.updateLearning(learningId, input);
    }
}
