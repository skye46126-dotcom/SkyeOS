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
            values.put("is_system", 1);
            values.put("is_active", 1);
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertWithOnConflict("tag", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }
}
