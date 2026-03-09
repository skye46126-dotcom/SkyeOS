package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class DeleteTagUseCase {
    private final LifeOsWriteRepository repository;

    public DeleteTagUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String tagId) {
        repository.deleteTag(tagId);
    }
}
