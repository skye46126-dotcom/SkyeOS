package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class DeleteRecordUseCase {
    private final LifeOsWriteRepository repository;

    @Inject
    public DeleteRecordUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String type, String recordId) {
        repository.deleteRecord(type, recordId);
    }
}
