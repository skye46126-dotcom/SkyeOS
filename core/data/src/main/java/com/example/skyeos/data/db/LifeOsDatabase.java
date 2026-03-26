package com.example.skyeos.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

public final class LifeOsDatabase {
    private static volatile LifeOsDatabase instance;
    private final Context appContext;
    private final LifeOsOpenHelper openHelper;

    private LifeOsDatabase(Context context) {
        this.appContext = context.getApplicationContext();
        this.openHelper = new LifeOsOpenHelper(this.appContext);
    }

    public static LifeOsDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (LifeOsDatabase.class) {
                if (instance == null) {
                    instance = new LifeOsDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void warmUp() {
        SQLiteDatabase db = openHelper.getWritableDatabase();
        db.close();
    }

    public SQLiteDatabase writableDb() {
        return openHelper.getWritableDatabase();
    }

    public SQLiteDatabase readableDb() {
        return openHelper.getReadableDatabase();
    }

    public File databaseFile() {
        return appContext.getDatabasePath(DbContract.DATABASE_NAME);
    }

    public synchronized void close() {
        openHelper.close();
    }
}
