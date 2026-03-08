package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.BackupResult;
import com.example.skyeos.domain.repository.LifeOsBackupRepository;

public final class GetLatestBackupUseCase {
    private final LifeOsBackupRepository repository;

    public GetLatestBackupUseCase(LifeOsBackupRepository repository) {
        this.repository = repository;
    }

    public BackupResult execute(String backupType) {
        return repository.getLatestBackup(backupType);
    }
}

