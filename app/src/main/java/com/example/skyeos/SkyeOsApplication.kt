package com.example.skyeos

import android.app.Application
import com.example.skyeos.data.db.LifeOsDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SkyeOsApplication : Application() {

    @Inject
    lateinit var database: LifeOsDatabase

    override fun onCreate() {
        super.onCreate()
        AppLocaleManager.applyStoredLocale(this)
        database.warmUp()
    }
}
