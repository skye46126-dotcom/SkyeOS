package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class DeleteProjectUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public DeleteProjectUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String projectId) {
        repository.deleteProject(projectId);
    }
}
