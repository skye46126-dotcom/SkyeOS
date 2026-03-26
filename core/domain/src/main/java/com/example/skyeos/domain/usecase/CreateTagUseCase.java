package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class CreateTagUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public CreateTagUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public String execute(String name, String emoji, String tagGroup, String scope, String parentTagId, int level) {
        return repository.createTag(name, emoji, tagGroup, scope, parentTagId, level);
    }
}
