package com.example.skyeos.data.di

import android.content.Context
import com.example.skyeos.data.auth.CurrentUserContext
import com.example.skyeos.data.auth.SQLiteCurrentUserContext
import com.example.skyeos.data.db.LifeOsDatabase
import com.example.skyeos.data.repository.SQLiteLifeOsBackupRepository
import com.example.skyeos.data.repository.SQLiteLifeOsMetricsRepository
import com.example.skyeos.data.repository.SQLiteLifeOsReadRepository
import com.example.skyeos.data.repository.SQLiteLifeOsReviewRepository
import com.example.skyeos.data.repository.SQLiteLifeOsWriteRepository
import com.example.skyeos.domain.repository.LifeOsBackupRepository
import com.example.skyeos.domain.repository.LifeOsMetricsRepository
import com.example.skyeos.domain.repository.LifeOsReadRepository
import com.example.skyeos.domain.repository.LifeOsReviewRepository
import com.example.skyeos.domain.repository.LifeOsWriteRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMetricsRepository(impl: SQLiteLifeOsMetricsRepository): LifeOsMetricsRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: SQLiteLifeOsBackupRepository): LifeOsBackupRepository

    @Binds
    @Singleton
    abstract fun bindReadRepository(impl: SQLiteLifeOsReadRepository): LifeOsReadRepository

    @Binds
    @Singleton
    abstract fun bindWriteRepository(impl: SQLiteLifeOsWriteRepository): LifeOsWriteRepository

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: SQLiteLifeOsReviewRepository): LifeOsReviewRepository

    @Binds
    @Singleton
    abstract fun bindUserContext(impl: SQLiteCurrentUserContext): CurrentUserContext

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): LifeOsDatabase {
            return LifeOsDatabase.getInstance(context)
        }
    }
}
