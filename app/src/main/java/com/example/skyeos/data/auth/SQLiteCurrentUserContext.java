package com.example.skyeos.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.Settings;
import android.text.TextUtils;

import com.example.skyeos.data.db.LifeOsDatabase;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class SQLiteCurrentUserContext implements CurrentUserContext {
    private static final String PREF_NAME = "lifeos_user_context";
    private static final String KEY_BOUND_USER_ID = "bound_user_id";
    private static final String KEY_DEVICE_FALLBACK_ID = "device_fallback_id";

    private final SharedPreferences prefs;
    private final Context appContext;
    private final LifeOsDatabase database;

    public SQLiteCurrentUserContext(Context context, LifeOsDatabase database) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.database = database;
    }

    @Override
    public String requireCurrentUserId() {
        String userId = getCurrentUserId();
        if (TextUtils.isEmpty(userId)) {
            throw new IllegalStateException("No current user in context");
        }
        return userId;
    }

    @Override
    public String getCurrentUserId() {
        String cached = prefs.getString(KEY_BOUND_USER_ID, null);
        if (!TextUtils.isEmpty(cached) && existsUser(cached)) {
            return cached;
        }
        return ensureBoundDeviceUser();
    }

    @Override
    public void setCurrentUserId(String userId) {
        // Keep compatibility for internal calls, but still validate existence.
        if (TextUtils.isEmpty(userId)) {
            prefs.edit().remove(KEY_BOUND_USER_ID).apply();
            return;
        }
        if (!existsUser(userId.trim())) {
            throw new IllegalArgumentException("User does not exist: " + userId);
        }
        prefs.edit().putString(KEY_BOUND_USER_ID, userId.trim()).apply();
    }

    private boolean existsUser(String userId) {
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM users WHERE id = ? AND status = 'active' LIMIT 1",
                new String[] { userId })) {
            return cursor.moveToFirst();
        }
    }

    private String ensureBoundDeviceUser() {
        String deviceKey = resolveStableDeviceKey();
        String username = "device_" + deviceKey;
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            String existingId = queryUserIdByUsername(db, username);
            if (!TextUtils.isEmpty(existingId)) {
                db.setTransactionSuccessful();
                prefs.edit().putString(KEY_BOUND_USER_ID, existingId).apply();
                return existingId;
            }

            String now = Instant.now().toString();
            String userId = UUID.randomUUID().toString();
            db.execSQL(
                    "INSERT INTO users(id, username, display_name, password_hash, status, timezone, currency_code, ideal_hourly_rate_cents, created_at, updated_at) " +
                            "VALUES(?, ?, ?, ?, 'active', 'Asia/Shanghai', 'CNY', 0, ?, ?)",
                    new Object[] { userId, username, "Tester-" + deviceKey.substring(0, Math.min(6, deviceKey.length())),
                            "__DEVICE_BIND__", now, now });
            db.setTransactionSuccessful();
            prefs.edit().putString(KEY_BOUND_USER_ID, userId).apply();
            return userId;
        } finally {
            db.endTransaction();
        }
    }

    private static String queryUserIdByUsername(SQLiteDatabase db, String username) {
        try (Cursor cursor = db.rawQuery(
                "SELECT id FROM users WHERE username = ? AND status = 'active' LIMIT 1",
                new String[] { username })) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        }
    }

    private String resolveStableDeviceKey() {
        String androidId = null;
        try {
            androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {
        }
        if (!TextUtils.isEmpty(androidId)) {
            return sanitize(androidId);
        }
        String fallback = prefs.getString(KEY_DEVICE_FALLBACK_ID, null);
        if (!TextUtils.isEmpty(fallback)) {
            return sanitize(fallback);
        }
        String generated = UUID.randomUUID().toString().replace("-", "");
        prefs.edit().putString(KEY_DEVICE_FALLBACK_ID, generated).apply();
        return sanitize(generated);
    }

    private static String sanitize(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            }
        }
        if (out.length() == 0) {
            return "unknown";
        }
        return out.toString();
    }
}
