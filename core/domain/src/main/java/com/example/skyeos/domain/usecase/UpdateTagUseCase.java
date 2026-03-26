package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class UpdateTagUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public UpdateTagUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String tagId, String name, String emoji, String tagGroup, String scope, String parentTagId, int level, boolean active) {
        repository.updateTag(tagId, name, emoji, tagGroup, scope, parentTagId, level, active);
    }
}
