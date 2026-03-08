package com.example.skyeos.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class LifeOsOpenHelper extends SQLiteOpenHelper {
    private final Context appContext;

    LifeOsOpenHelper(Context context) {
        super(context, DbContract.DATABASE_NAME, null, DbContract.DATABASE_VERSION);
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        applyMigrations(db, 0, DbContract.DATABASE_VERSION);
        ensureBootstrapData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        applyMigrations(db, oldVersion, newVersion);
        ensureBootstrapData(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureBootstrapData(db);
    }

    private void applyMigrations(SQLiteDatabase db, int oldVersion, int newVersion) {
        List<MigrationSpec> migrations = MigrationRegistry.all();
        db.beginTransaction();
        try {
            for (MigrationSpec migration : migrations) {
                if (migration.version > oldVersion && migration.version <= newVersion) {
                    SqlScriptExecutor.executeAssetSql(appContext, db, migration.assetPath);
                    insertOrReplaceMigrationRecord(db, migration);
                }
            }
            db.setTransactionSuccessful();
        } catch (IOException e) {
            throw new IllegalStateException("Failed applying migrations", e);
        } finally {
            db.endTransaction();
        }
    }

    private void insertOrReplaceMigrationRecord(SQLiteDatabase db, MigrationSpec migration) {
        if (!migrationTableExists(db)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(DbContract.MigrationTable.COL_VERSION, migration.version);
        values.put(DbContract.MigrationTable.COL_NAME, migration.name);
        values.put(DbContract.MigrationTable.COL_CHECKSUM, migration.checksum);
        values.put(DbContract.MigrationTable.COL_APPLIED_AT, Instant.now().toString());
        db.insertWithOnConflict(
                DbContract.MigrationTable.NAME,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    private boolean migrationTableExists(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                new String[]{DbContract.MigrationTable.NAME}
        )) {
            return cursor.moveToFirst();
        }
    }

    private void ensureBootstrapData(SQLiteDatabase db) {
        ensureUserProfile(db);
        ensureDefaultUser(db);
        ensureOwnershipBackfill(db);
        ensureSystemTags(db);
    }

    private void ensureUserProfile(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT id FROM user_profile LIMIT 1", null)) {
            if (cursor.moveToFirst()) {
                return;
            }
        }
        String now = Instant.now().toString();
        ContentValues values = new ContentValues();
        values.put("id", UUID.randomUUID().toString());
        values.put("display_name", "LifeOS User");
        values.put("timezone", "Asia/Shanghai");
        values.put("currency_code", "CNY");
        values.put("ideal_hourly_rate_cents", 0);
        values.put("created_at", now);
        values.put("updated_at", now);
        db.insertWithOnConflict("user_profile", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void ensureSystemTags(SQLiteDatabase db) {
        String[] tags = new String[]{"赚钱", "成长", "快乐", "健康", "关系", "自由", "探索"};
        String now = Instant.now().toString();
        for (String tagName : tags) {
            if (TextUtils.isEmpty(tagName)) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put("id", UUID.randomUUID().toString());
            values.put("name", tagName);
            values.put("tag_group", "value");
            values.put("scope", "global");
            values.put("sort_order", 0);
            values.put("is_system", 1);
            values.put("is_active", 1);
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertWithOnConflict("tag", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private void ensureDefaultUser(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT id FROM users LIMIT 1", null)) {
            if (cursor.moveToFirst()) {
                return;
            }
        } catch (Exception ignored) {
            return;
        }
        String profileId = null;
        String displayName = "Owner";
        String timezone = "Asia/Shanghai";
        String currency = "CNY";
        long idealHourlyRate = 0L;
        try (Cursor cursor = db.rawQuery(
                "SELECT id, display_name, timezone, currency_code, ideal_hourly_rate_cents FROM user_profile LIMIT 1",
                null)) {
            if (cursor.moveToFirst()) {
                profileId = cursor.getString(0);
                if (!cursor.isNull(1)) {
                    displayName = cursor.getString(1);
                }
                if (!cursor.isNull(2)) {
                    timezone = cursor.getString(2);
                }
                if (!cursor.isNull(3)) {
                    currency = cursor.getString(3);
                }
                if (!cursor.isNull(4)) {
                    idealHourlyRate = cursor.getLong(4);
                }
            }
        } catch (Exception ignored) {
            return;
        }
        String now = Instant.now().toString();
        ContentValues values = new ContentValues();
        values.put("id", TextUtils.isEmpty(profileId) ? UUID.randomUUID().toString() : profileId);
        values.put("username", "owner");
        values.put("display_name", displayName);
        values.put("password_hash", "__SET_ME__");
        values.put("status", "active");
        values.put("timezone", timezone);
        values.put("currency_code", currency);
        values.put("ideal_hourly_rate_cents", idealHourlyRate);
        values.put("created_at", now);
        values.put("updated_at", now);
        db.insertWithOnConflict("users", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void ensureOwnershipBackfill(SQLiteDatabase db) {
        String ownerId = null;
        try (Cursor cursor = db.rawQuery("SELECT id FROM users ORDER BY created_at ASC LIMIT 1", null)) {
            if (cursor.moveToFirst()) {
                ownerId = cursor.getString(0);
            }
        } catch (Exception ignored) {
            return;
        }
        if (TextUtils.isEmpty(ownerId)) {
            return;
        }
        String[] tables = new String[] {
                "project", "tag", "time_log", "income", "expense", "learning_record",
                "daily_review", "metric_snapshot", "backup_record", "restore_record", "audit_log"
        };
        for (String table : tables) {
            try {
                ContentValues values = new ContentValues();
                values.put("owner_user_id", ownerId);
                db.update(table, values, "owner_user_id IS NULL OR owner_user_id = ''", null);
            } catch (Exception ignored) {
            }
        }
        try {
            db.execSQL(
                    "INSERT OR IGNORE INTO project_member(project_id, user_id, role, created_at) " +
                            "SELECT id, owner_user_id, 'owner', COALESCE(created_at, ?) FROM project WHERE owner_user_id IS NOT NULL AND owner_user_id <> ''",
                    new Object[] { Instant.now().toString() });
        } catch (Exception ignored) {
        }
    }
}
