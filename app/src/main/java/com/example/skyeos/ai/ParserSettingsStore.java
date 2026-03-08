package com.example.skyeos.ai;

import android.content.Context;
import android.content.SharedPreferences;

public final class ParserSettingsStore {
    private static final String PREF = "lifeos_parser_settings";
    private static final String KEY_MODE = "parser_mode";

    private final SharedPreferences sharedPreferences;

    public ParserSettingsStore(Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public ParserMode loadMode() {
        String mode = sharedPreferences.getString(KEY_MODE, "auto");
        return ParserMode.fromString(mode);
    }

    public void saveMode(ParserMode mode) {
        sharedPreferences.edit().putString(KEY_MODE, mode == null ? "auto" : mode.name().toLowerCase()).apply();
    }
}

