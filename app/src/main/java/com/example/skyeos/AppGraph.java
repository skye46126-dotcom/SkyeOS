package com.example.skyeos;

import android.content.Context;

import com.example.skyeos.ai.AiParseOrchestrator;
import com.example.skyeos.ai.ParserSettingsStore;
import com.example.skyeos.ai.RuleParserEngine;
import com.example.skyeos.ai.VcpParserEngine;
import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.data.repository.SQLiteLifeOsBackupRepository;
import com.example.skyeos.data.repository.SQLiteLifeOsMetricsRepository;
import com.example.skyeos.data.repository.SQLiteLifeOsReadRepository;
import com.example.skyeos.data.repository.SQLiteLifeOsWriteRepository;
import com.example.skyeos.domain.repository.LifeOsBackupRepository;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;
import com.example.skyeos.domain.repository.LifeOsReadRepository;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;
import com.example.skyeos.domain.usecase.CreateBackupUseCase;
import com.example.skyeos.domain.usecase.CreateExpenseUseCase;
import com.example.skyeos.domain.usecase.CreateIncomeUseCase;
import com.example.skyeos.domain.usecase.CreateLearningUseCase;
import com.example.skyeos.domain.usecase.CreateProjectUseCase;
import com.example.skyeos.domain.usecase.CreateTimeLogUseCase;
import com.example.skyeos.domain.usecase.GetLatestBackupUseCase;
import com.example.skyeos.domain.usecase.GetMetricSnapshotUseCase;
import com.example.skyeos.domain.usecase.GetOverviewUseCase;
import com.example.skyeos.domain.usecase.GetProjectOptionsUseCase;
import com.example.skyeos.domain.usecase.GetRecentRecordsUseCase;
import com.example.skyeos.domain.usecase.LifeOsUseCases;
import com.example.skyeos.domain.usecase.RegisterExternalBackupUseCase;
import com.example.skyeos.domain.usecase.RecomputeMetricSnapshotUseCase;
import com.example.skyeos.domain.usecase.RestoreBackupUseCase;

public final class AppGraph {
    private static volatile AppGraph instance;

    public final LifeOsDatabase database;
    public final LifeOsWriteRepository writeRepository;
    public final LifeOsReadRepository readRepository;
    public final LifeOsMetricsRepository metricsRepository;
    public final LifeOsBackupRepository backupRepository;
    public final ParserSettingsStore parserSettingsStore;
    public final AiParseOrchestrator aiParseOrchestrator;
    public final LifeOsUseCases useCases;

    private AppGraph(Context context) {
        this.database = LifeOsDatabase.getInstance(context);
        this.writeRepository = new SQLiteLifeOsWriteRepository(database);
        this.readRepository = new SQLiteLifeOsReadRepository(database);
        this.metricsRepository = new SQLiteLifeOsMetricsRepository(database);
        this.backupRepository = new SQLiteLifeOsBackupRepository(context, database);
        this.parserSettingsStore = new ParserSettingsStore(context);
        this.aiParseOrchestrator = new AiParseOrchestrator(
                new VcpParserEngine(),
                new RuleParserEngine(),
                parserSettingsStore.loadMode()
        );
        this.useCases = new LifeOsUseCases(
                new CreateProjectUseCase(writeRepository),
                new CreateTimeLogUseCase(writeRepository),
                new CreateIncomeUseCase(writeRepository),
                new CreateExpenseUseCase(writeRepository),
                new CreateLearningUseCase(writeRepository),
                new RecomputeMetricSnapshotUseCase(metricsRepository),
                new GetMetricSnapshotUseCase(metricsRepository),
                new GetOverviewUseCase(readRepository),
                new GetRecentRecordsUseCase(readRepository),
                new GetProjectOptionsUseCase(readRepository),
                new CreateBackupUseCase(backupRepository),
                new RegisterExternalBackupUseCase(backupRepository),
                new RestoreBackupUseCase(backupRepository),
                new GetLatestBackupUseCase(backupRepository)
        );
    }

    public static AppGraph getInstance(Context context) {
        if (instance == null) {
            synchronized (AppGraph.class) {
                if (instance == null) {
                    instance = new AppGraph(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
}
