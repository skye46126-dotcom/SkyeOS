package com.example.skyeos;

import android.app.Application;

public class SkyeOsApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppLocaleManager.applyStoredLocale(this);
        AppGraph.getInstance(this).database.warmUp();
    }
}
