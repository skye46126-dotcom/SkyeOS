package com.example.skyeos.domain.model;

public final class RestoreResult {
    public final String id;
    public final String backupRecordId;
    public final boolean success;
    public final String errorMessage;
    public final String restoredAt;

    public RestoreResult(String id, String backupRecordId, boolean success, String errorMessage, String restoredAt) {
        this.id = id;
        this.backupRecordId = backupRecordId;
        this.success = success;
        this.errorMessage = errorMessage;
        this.restoredAt = restoredAt;
    }
}

