package com.example.skyeos.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.domain.model.BackupResult;
import com.example.skyeos.domain.model.RestoreResult;
import com.example.skyeos.domain.repository.LifeOsBackupRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SQLiteLifeOsBackupRepository implements LifeOsBackupRepository {
    private static final Set<String> BACKUP_TYPES = Set.of(
            "daily_incremental",
            "weekly_full",
            "monthly_archive",
            "manual"
    );

    private final Context appContext;
    private final LifeOsDatabase database;
    private final CurrentUserContext userContext;

    public SQLiteLifeOsBackupRepository(Context context, LifeOsDatabase database, CurrentUserContext userContext) {
        this.appContext = context.getApplicationContext();
        this.database = database;
        this.userContext = userContext;
    }

    @Override
    public BackupResult createBackup(String backupType) {
        String type = normalizeBackupType(backupType);
        String userId = userContext.requireCurrentUserId();
        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        File backupFile = null;
        String checksum = null;
        long fileSize = 0L;
        try {
            SQLiteDatabase db = database.writableDb();
            // PRAGMA wal_checkpoint returns rows, so it must be executed via query API.
            try (Cursor ignored = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                // no-op: execute checkpoint to flush WAL before file copy
            } catch (SQLException ignored) {
                // Continue backup even if checkpoint is not available on some SQLite variants.
            }
            database.close();

            File sourceDb = database.databaseFile();
            if (!sourceDb.exists()) {
                database.warmUp();
                database.close();
            }
            sourceDb = database.databaseFile();
            if (!sourceDb.exists()) {
                throw new IllegalStateException("Database file does not exist for backup");
            }

            backupFile = createBackupFile(type, createdAt);
            Files.copy(sourceDb.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            fileSize = backupFile.length();
            checksum = sha256(backupFile);

            database.warmUp();
            insertBackupRecord(id, userId, type, backupFile.getAbsolutePath(), fileSize, checksum, "success", null, createdAt);
            return new BackupResult(id, type, backupFile.getAbsolutePath(), fileSize, checksum, true, null, createdAt);
        } catch (Exception e) {
            database.warmUp();
            String filePath = backupFile == null ? null : backupFile.getAbsolutePath();
            insertBackupRecord(id, userId, type, filePath, fileSize, checksum, "failed", e.getMessage(), createdAt);
            return new BackupResult(id, type, filePath, fileSize, checksum, false, e.getMessage(), createdAt);
        }
    }

    @Override
    public BackupResult registerExternalBackup(String filePath, String backupType, long fileSizeBytes, String checksum) {
        if (TextUtils.isEmpty(filePath) || TextUtils.isEmpty(filePath.trim())) {
            throw new IllegalArgumentException("filePath is required");
        }
        String type = normalizeBackupType(backupType);
        String userId = userContext.requireCurrentUserId();
        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        insertBackupRecord(
                id,
                userId,
                type,
                filePath.trim(),
                Math.max(0L, fileSizeBytes),
                checksum,
                "success",
                null,
                createdAt
        );
        return new BackupResult(
                id,
                type,
                filePath.trim(),
                Math.max(0L, fileSizeBytes),
                checksum,
                true,
                null,
                createdAt
        );
    }

    @Override
    public RestoreResult restoreFromBackupRecord(String backupRecordId) {
        if (TextUtils.isEmpty(backupRecordId) || TextUtils.isEmpty(backupRecordId.trim())) {
            throw new IllegalArgumentException("backupRecordId is required");
        }
        String userId = userContext.requireCurrentUserId();
        BackupRecord record = queryBackupRecord(backupRecordId.trim());
        String restoreId = UUID.randomUUID().toString();
        String restoredAt = Instant.now().toString();
        if (record == null || !"success".equals(record.status) || TextUtils.isEmpty(record.filePath)) {
            insertRestoreRecord(restoreId, userId, null, "failed", "backup record not found or invalid", restoredAt);
            return new RestoreResult(restoreId, backupRecordId, false, "backup record not found or invalid", restoredAt);
        }
        try {
            File backupFile = new File(record.filePath);
            if (!backupFile.exists()) {
                throw new IllegalStateException("Backup file not found: " + record.filePath);
            }
            database.close();
            File targetDb = database.databaseFile();
            Files.copy(backupFile.toPath(), targetDb.toPath(), StandardCopyOption.REPLACE_EXISTING);
            database.warmUp();
            insertRestoreRecord(restoreId, userId, backupRecordId, "success", null, restoredAt);
            return new RestoreResult(restoreId, backupRecordId, true, null, restoredAt);
        } catch (Exception e) {
            database.warmUp();
            insertRestoreRecord(restoreId, userId, backupRecordId, "failed", e.getMessage(), restoredAt);
            return new RestoreResult(restoreId, backupRecordId, false, e.getMessage(), restoredAt);
        }
    }

    @Override
    public BackupResult getLatestBackup(String backupType) {
        String type = normalizeBackupType(backupType);
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, backup_type, file_path, file_size_bytes, checksum, status, error_message, created_at " +
                        "FROM backup_record WHERE owner_user_id = ? AND backup_type = ? ORDER BY created_at DESC LIMIT 1",
                new String[]{userId, type}
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return mapBackup(cursor);
        }
    }

    private void insertBackupRecord(
            String id,
            String userId,
            String type,
            String filePath,
            long fileSize,
            String checksum,
            String status,
            String error,
            String createdAt
    ) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("owner_user_id", userId);
        values.put("backup_type", type);
        values.put("file_path", filePath == null ? "" : filePath);
        values.put("file_size_bytes", fileSize);
        values.put("checksum", checksum);
        values.put("status", status);
        values.put("error_message", error);
        values.put("created_at", createdAt);
        database.writableDb().insertWithOnConflict("backup_record", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void insertRestoreRecord(String id, String userId, String backupRecordId, String status, String error, String restoredAt) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("owner_user_id", userId);
        values.put("backup_record_id", backupRecordId);
        values.put("status", status);
        values.put("error_message", error);
        values.put("restored_at", restoredAt);
        database.writableDb().insertWithOnConflict("restore_record", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private BackupRecord queryBackupRecord(String id) {
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, backup_type, file_path, file_size_bytes, checksum, status, error_message, created_at FROM backup_record WHERE id = ? AND owner_user_id = ? LIMIT 1",
                new String[]{id, userId}
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            BackupRecord result = new BackupRecord();
            result.id = cursor.getString(0);
            result.backupType = cursor.getString(1);
            result.filePath = cursor.getString(2);
            result.fileSizeBytes = cursor.isNull(3) ? 0L : cursor.getLong(3);
            result.checksum = cursor.getString(4);
            result.status = cursor.getString(5);
            result.errorMessage = cursor.getString(6);
            result.createdAt = cursor.getString(7);
            return result;
        }
    }

    private static BackupResult mapBackup(Cursor cursor) {
        return new BackupResult(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.isNull(3) ? 0L : cursor.getLong(3),
                cursor.getString(4),
                "success".equals(cursor.getString(5)),
                cursor.getString(6),
                cursor.getString(7)
        );
    }

    private File createBackupFile(String backupType, String createdAt) throws IOException {
        File root = new File(appContext.getFilesDir(), "lifeos_backups");
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Cannot create backup root directory");
        }
        File folder = new File(root, backupFolderName(backupType));
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Cannot create backup type directory");
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
                .withZone(ZoneOffset.UTC)
                .format(Instant.parse(createdAt));
        return new File(folder, "lifeos_" + backupType + "_" + timestamp + ".db");
    }

    private static String backupFolderName(String type) {
        switch (type) {
            case "daily_incremental":
                return "daily";
            case "weekly_full":
                return "weekly";
            case "monthly_archive":
                return "monthly";
            case "manual":
                return "manual";
            default:
                throw new IllegalArgumentException("Unsupported backup type: " + type);
        }
    }

    private static String normalizeBackupType(String backupType) {
        String value = TextUtils.isEmpty(backupType) ? "manual" : backupType.trim().toLowerCase(Locale.US);
        if (!BACKUP_TYPES.contains(value)) {
            throw new IllegalArgumentException("Invalid backupType: " + value);
        }
        return value;
    }

    private static String sha256(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    private static final class BackupRecord {
        String id;
        String backupType;
        String filePath;
        long fileSizeBytes;
        String checksum;
        String status;
        String errorMessage;
        String createdAt;
    }
}
