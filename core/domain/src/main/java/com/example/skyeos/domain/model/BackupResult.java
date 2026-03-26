package com.example.skyeos.domain.model;

public final class BackupResult {
    public final String id;
    public final String backupType;
    public final String filePath;
    public final long fileSizeBytes;
    public final String checksum;
    public final boolean success;
    public final String errorMessage;
    public final String createdAt;

    public BackupResult(
            String id,
            String backupType,
            String filePath,
            long fileSizeBytes,
            String checksum,
            boolean success,
            String errorMessage,
            String createdAt
    ) {
        this.id = id;
        this.backupType = backupType;
        this.filePath = filePath;
        this.fileSizeBytes = fileSizeBytes;
        this.checksum = checksum;
        this.success = success;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }
}

