package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.ProjectOption;
import com.example.skyeos.domain.repository.LifeOsReadRepository;

import java.util.List;

public final class GetProjectOptionsUseCase {
    private final LifeOsReadRepository repository;

    @Inject
    public GetProjectOptionsUseCase(LifeOsReadRepository repository) {
        this.repository = repository;
    }

    public List<ProjectOption> execute(boolean includeDone) {
        return repository.getProjectOptions(includeDone);
    }
}

