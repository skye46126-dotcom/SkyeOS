package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.BackupResult;
import com.example.skyeos.domain.repository.LifeOsBackupRepository;

public final class RegisterExternalBackupUseCase {
    private final LifeOsBackupRepository repository;

    public RegisterExternalBackupUseCase(LifeOsBackupRepository repository) {
        this.repository = repository;
    }

    public BackupResult execute(String filePath, String backupType, long fileSizeBytes, String checksum) {
        return repository.registerExternalBackup(filePath, backupType, fileSizeBytes, checksum);
    }
}

