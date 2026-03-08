package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateTagUseCase {
    private final LifeOsWriteRepository repository;

    public CreateTagUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(String name, String emoji, String tagGroup, String scope) {
        return repository.createTag(name, emoji, tagGroup, scope);
    }
}
