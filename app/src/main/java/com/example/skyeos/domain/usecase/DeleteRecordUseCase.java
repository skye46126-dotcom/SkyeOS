package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class DeleteRecordUseCase {
    private final LifeOsWriteRepository repository;

    public DeleteRecordUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String type, String recordId) {
        repository.deleteRecord(type, recordId);
    }
}
