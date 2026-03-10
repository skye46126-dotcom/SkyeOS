package com.example.skyeos.ui.util;

import android.content.Context;

import com.example.skyeos.R;

public final class UiFormatters {

    private UiFormatters() {
    }

    public static String yuan(Context context, long cents) {
        return yuan(context, cents, true);
    }

    public static String yuan(Context context, long cents, boolean zeroAsDash) {
        if (zeroAsDash && cents == 0L) {
            return context.getString(R.string.common_none);
        }
        if (cents % 100 == 0L) {
            return context.getString(R.string.common_currency_yuan_int, cents / 100L);
        }
        return context.getString(R.string.common_currency_yuan, cents / 100.0);
    }

    public static String duration(Context context, long minutes) {
        if (minutes <= 0L) {
            return context.getString(R.string.common_none);
        }
        return context.getString(R.string.common_duration_hours_minutes, minutes / 60L, minutes % 60L);
    }

    public static String hourly(Context context, long cents) {
        return context.getString(R.string.common_hourly_format, yuan(context, cents));
    }

    public static String nullableHourly(Context context, Long cents) {
        return cents == null ? context.getString(R.string.common_none) : hourly(context, cents);
    }
}

