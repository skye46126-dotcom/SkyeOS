package com.example.skyeos.domain.repository;

import com.example.skyeos.domain.model.BackupResult;
import com.example.skyeos.domain.model.RestoreResult;

public interface LifeOsBackupRepository {
    BackupResult createBackup(String backupType);

    BackupResult registerExternalBackup(String filePath, String backupType, long fileSizeBytes, String checksum);

    RestoreResult restoreFromBackupRecord(String backupRecordId);

    BackupResult getLatestBackup(String backupType);
}
