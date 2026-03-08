package com.example.skyeos.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class SqlScriptExecutor {
    private SqlScriptExecutor() {}

    static void executeAssetSql(Context context, SQLiteDatabase db, String assetPath) throws IOException {
        String script = readAsset(context, assetPath);
        for (String statement : script.split(";")) {
            String sql = statement.trim();
            if (!sql.isEmpty()) {
                db.execSQL(sql);
            }
        }
    }

    private static String readAsset(Context context, String assetPath) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = context.getAssets().open(assetPath);
             InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}

