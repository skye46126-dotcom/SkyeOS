package com.example.skyeos.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import com.example.skyeos.data.auth.CurrentUserContext
import com.example.skyeos.data.db.LifeOsDatabase
import com.example.skyeos.data.db.getLongOrNull
import com.example.skyeos.data.db.getStringOrNull
import com.example.skyeos.domain.model.BackupResult
import com.example.skyeos.domain.model.RestoreResult
import com.example.skyeos.domain.repository.LifeOsBackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class SQLiteLifeOsBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LifeOsDatabase,
    private val userContext: CurrentUserContext
) : LifeOsBackupRepository {

    private val appContext: Context = context.applicationContext

    companion object {
        private val BACKUP_TYPES = setOf(
            "daily_incremental",
            "weekly_full",
            "monthly_archive",
            "manual"
        )

        private fun normalizeBackupType(backupType: String?): String {
            val value = backupType?.trim()?.lowercase(Locale.US).takeIf { !it.isNullOrEmpty() } ?: "manual"
            require(BACKUP_TYPES.contains(value)) { "Invalid backupType: $value" }
            return value
        }

        private fun backupFolderName(type: String): String = when (type) {
            "daily_incremental" -> "daily"
            "weekly_full" -> "weekly"
            "monthly_archive" -> "monthly"
            "manual" -> "manual"
            else -> throw IllegalArgumentException("Unsupported backup type: $type")
        }

        private fun sha256(file: File): String {
            return FileInputStream(file).use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }

        private fun mapBackup(cursor: Cursor): BackupResult {
            return BackupResult(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getLongOrNull(3) ?: 0L,
                cursor.getString(4),
                "success" == cursor.getString(5),
                cursor.getString(6),
                cursor.getString(7)
            )
        }
    }

    override fun createBackup(backupType: String?): BackupResult {
        val type = normalizeBackupType(backupType)
        val userId = userContext.requireCurrentUserId()
        val id = UUID.randomUUID().toString()
        val createdAt = Instant.now().toString()
        var backupFile: File? = null
        var checksum: String? = null
        var fileSize = 0L

        return try {
            database.writableDb().use { db ->
                try {
                    db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { }
                } catch (ignored: SQLException) { }
            }
            database.close()

            var sourceDb = database.databaseFile()
            if (!sourceDb.exists()) {
                database.warmUp()
                database.close()
                sourceDb = database.databaseFile()
            }
            check(sourceDb.exists()) { "Database file does not exist for backup" }

            backupFile = createBackupFile(type, createdAt)
            Files.copy(sourceDb.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            fileSize = backupFile.length()
            checksum = sha256(backupFile)

            database.warmUp()
            insertBackupRecord(id, userId, type, backupFile.absolutePath, fileSize, checksum, "success", null, createdAt)
            BackupResult(id, type, backupFile.absolutePath, fileSize, checksum, true, null, createdAt)
        } catch (e: Exception) {
            database.warmUp()
            val filePath = backupFile?.absolutePath
            insertBackupRecord(id, userId, type, filePath, fileSize, checksum, "failed", e.message, createdAt)
            BackupResult(id, type, filePath, fileSize, checksum, false, e.message, createdAt)
        }
    }

    override fun registerExternalBackup(filePath: String?, backupType: String?, fileSizeBytes: Long, checksum: String?): BackupResult {
        require(!filePath.isNullOrBlank()) { "filePath is required" }
        val type = normalizeBackupType(backupType)
        val userId = userContext.requireCurrentUserId()
        val id = UUID.randomUUID().toString()
        val createdAt = Instant.now().toString()
        val size = fileSizeBytes.coerceAtLeast(0L)

        insertBackupRecord(id, userId, type, filePath.trim(), size, checksum, "success", null, createdAt)
        return BackupResult(id, type, filePath.trim(), size, checksum, true, null, createdAt)
    }

    override fun restoreFromBackupRecord(backupRecordId: String?): RestoreResult {
        require(!backupRecordId.isNullOrBlank()) { "backupRecordId is required" }
        val userId = userContext.requireCurrentUserId()
        val record = queryBackupRecord(backupRecordId.trim())
        val restoreId = UUID.randomUUID().toString()
        val restoredAt = Instant.now().toString()

        if (record == null || record.status != "success" || record.filePath.isNullOrEmpty()) {
            insertRestoreRecord(restoreId, userId, null, "failed", "backup record not found or invalid", restoredAt)
            return RestoreResult(restoreId, backupRecordId.trim(), false, "backup record not found or invalid", restoredAt)
        }

        return try {
            val backupFile = File(record.filePath)
            check(backupFile.exists()) { "Backup file not found: ${record.filePath}" }

            database.close()
            val targetDb = database.databaseFile()
            Files.copy(backupFile.toPath(), targetDb.toPath(), StandardCopyOption.REPLACE_EXISTING)
            database.warmUp()

            insertRestoreRecord(restoreId, userId, backupRecordId.trim(), "success", null, restoredAt)
            RestoreResult(restoreId, backupRecordId.trim(), true, null, restoredAt)
        } catch (e: Exception) {
            database.warmUp()
            insertRestoreRecord(restoreId, userId, backupRecordId.trim(), "failed", e.message, restoredAt)
            RestoreResult(restoreId, backupRecordId.trim(), false, e.message, restoredAt)
        }
    }

    override fun getLatestBackup(backupType: String?): BackupResult? {
        val type = normalizeBackupType(backupType)
        val userId = userContext.requireCurrentUserId()
        return database.readableDb().rawQuery(
            """
            SELECT id, backup_type, file_path, file_size_bytes, checksum, status, error_message, created_at 
            FROM backup_record 
            WHERE owner_user_id = ? AND backup_type = ? 
            ORDER BY created_at DESC LIMIT 1
            """.trimIndent(),
            arrayOf(userId, type)
        ).use { cursor ->
            if (cursor.moveToFirst()) mapBackup(cursor) else null
        }
    }

    private fun insertBackupRecord(
        id: String, userId: String, type: String, filePath: String?, fileSize: Long,
        checksum: String?, status: String, error: String?, createdAt: String
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("owner_user_id", userId)
            put("backup_type", type)
            put("file_path", filePath ?: "")
            put("file_size_bytes", fileSize)
            put("checksum", checksum)
            put("status", status)
            put("error_message", error)
            put("created_at", createdAt)
        }
        database.writableDb().insertWithOnConflict("backup_record", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertRestoreRecord(id: String, userId: String, backupRecordId: String?, status: String, error: String?, restoredAt: String) {
        val values = ContentValues().apply {
            put("id", id)
            put("owner_user_id", userId)
            put("backup_record_id", backupRecordId)
            put("status", status)
            put("error_message", error)
            put("restored_at", restoredAt)
        }
        database.writableDb().insertWithOnConflict("restore_record", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun queryBackupRecord(id: String): BackupRecord? {
        val userId = userContext.requireCurrentUserId()
        return database.readableDb().rawQuery(
            """
            SELECT id, backup_type, file_path, file_size_bytes, checksum, status, error_message, created_at 
            FROM backup_record 
            WHERE id = ? AND owner_user_id = ? LIMIT 1
            """.trimIndent(),
            arrayOf(id, userId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                BackupRecord(
                    id = cursor.getString(0),
                    backupType = cursor.getString(1),
                    filePath = cursor.getString(2),
                    fileSizeBytes = cursor.getLongOrNull(3) ?: 0L,
                    checksum = cursor.getString(4),
                    status = cursor.getString(5),
                    errorMessage = cursor.getString(6),
                    createdAt = cursor.getString(7)
                )
            } else null
        }
    }

    private fun createBackupFile(backupType: String, createdAt: String): File {
        val root = File(appContext.filesDir, "lifeos_backups").apply {
            if (!exists() && !mkdirs()) throw java.io.IOException("Cannot create backup root directory")
        }
        val folder = File(root, backupFolderName(backupType)).apply {
            if (!exists() && !mkdirs()) throw java.io.IOException("Cannot create backup type directory")
        }
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
            .withZone(ZoneOffset.UTC)
            .format(Instant.parse(createdAt))
        return File(folder, "lifeos_${backupType}_${timestamp}.db")
    }

    private data class BackupRecord(
        val id: String,
        val backupType: String,
        val filePath: String?,
        val fileSizeBytes: Long,
        val checksum: String?,
        val status: String,
        val errorMessage: String?,
        val createdAt: String
    )
}
