package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class DeleteProjectUseCase {
    private final LifeOsWriteRepository repository;

    public DeleteProjectUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String projectId) {
        repository.deleteProject(projectId);
    }
}
