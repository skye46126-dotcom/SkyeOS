package com.example.skyeos;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class AppLocaleManager {
    private static final String PREFS = "lifeos_locale";
    private static final String KEY_LANGUAGE = "language_tag";

    private AppLocaleManager() {
    }

    public static void applyStoredLocale(Context context) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(loadLanguageTag(context)));
    }

    public static void saveLanguageTag(Context context, String languageTag) {
        prefs(context).edit().putString(KEY_LANGUAGE, normalize(languageTag)).apply();
        applyStoredLocale(context);
    }

    public static String loadLanguageTag(Context context) {
        return normalize(prefs(context).getString(KEY_LANGUAGE, ""));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalize(String languageTag) {
        if (languageTag == null) {
            return "";
        }
        String value = languageTag.trim();
        if ("zh".equalsIgnoreCase(value) || "zh-cn".equalsIgnoreCase(value)) {
            return "zh-CN";
        }
        if ("en".equalsIgnoreCase(value) || "en-us".equalsIgnoreCase(value)) {
            return "en";
        }
        return "";
    }
}
