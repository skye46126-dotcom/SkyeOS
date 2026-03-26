package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.RestoreResult;
import com.example.skyeos.domain.repository.LifeOsBackupRepository;

public final class RestoreBackupUseCase {
    private final LifeOsBackupRepository repository;

    @Inject
    public RestoreBackupUseCase(LifeOsBackupRepository repository) {
        this.repository = repository;
    }

    public RestoreResult execute(String backupRecordId) {
        return repository.restoreFromBackupRecord(backupRecordId);
    }
}

