package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.input.CreateProjectInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class UpdateProjectRecordUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public UpdateProjectRecordUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String projectId, CreateProjectInput input) {
        repository.updateProjectRecord(projectId, input);
    }
}
