package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.BackupResult;
import com.example.skyeos.domain.repository.LifeOsBackupRepository;

public final class GetLatestBackupUseCase {
    private final LifeOsBackupRepository repository;

    @Inject
    public GetLatestBackupUseCase(LifeOsBackupRepository repository) {
        this.repository = repository;
    }

    public BackupResult execute(String backupType) {
        return repository.getLatestBackup(backupType);
    }
}

