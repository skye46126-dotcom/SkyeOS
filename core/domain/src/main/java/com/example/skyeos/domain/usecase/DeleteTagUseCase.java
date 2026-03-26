package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class DeleteTagUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public DeleteTagUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String tagId) {
        repository.deleteTag(tagId);
    }
}
