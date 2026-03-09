package com.example.skyeos;

import android.content.Context;

import com.example.skyeos.ai.AiParseOrchestrator;
import com.example.skyeos.ai.AiApiConfigStore;
import com.example.skyeos.ai.LlmApiParserEngine;
import com.example.skyeos.ai.ParserSettingsStore;
import com.example.skyeos.ai.RuleParserEngine;
import com.example.skyeos.ai.VcpParserEngine;
import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.auth.SQLiteCurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.data.repository.SQLiteLifeOsBackupRepository;
import com.example.skyeos.data.repository.SQLiteLifeOsMetricsRepository;
import com.example.skyeos.data.repository.SQLiteLifeOsReadRepository;
import com.example.skyeos.data.repository.SQLiteLifeOsReviewRepository;
import com.example.skyeos.data.repository.SQLiteLifeOsWriteRepository;
import com.example.skyeos.domain.repository.LifeOsBackupRepository;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;
import com.example.skyeos.domain.repository.LifeOsReadRepository;
import com.example.skyeos.domain.repository.LifeOsReviewRepository;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;
import com.example.skyeos.domain.usecase.CreateBackupUseCase;
import com.example.skyeos.domain.usecase.CreateExpenseUseCase;
import com.example.skyeos.domain.usecase.CreateIncomeUseCase;
import com.example.skyeos.domain.usecase.CreateLearningUseCase;
import com.example.skyeos.domain.usecase.CreateProjectUseCase;
import com.example.skyeos.domain.usecase.CreateTagUseCase;
import com.example.skyeos.domain.usecase.CreateTimeLogUseCase;
import com.example.skyeos.domain.usecase.CreateCapexCostUseCase;
import com.example.skyeos.domain.usecase.CreateRecurringCostRuleUseCase;
import com.example.skyeos.domain.usecase.DeleteCapexCostUseCase;
import com.example.skyeos.domain.usecase.DeleteProjectUseCase;
import com.example.skyeos.domain.usecase.DeleteRecurringCostRuleUseCase;
import com.example.skyeos.domain.usecase.DeleteRecordUseCase;
import com.example.skyeos.domain.usecase.DeleteTagUseCase;
import com.example.skyeos.domain.usecase.GetLatestBackupUseCase;
import com.example.skyeos.domain.usecase.GetMonthlyCostBaselineUseCase;
import com.example.skyeos.domain.usecase.GetMetricSnapshotUseCase;
import com.example.skyeos.domain.usecase.GetRateComparisonUseCase;
import com.example.skyeos.domain.usecase.GetIdealHourlyRateUseCase;
import com.example.skyeos.domain.usecase.GetCurrentMonthBasicLivingCostUseCase;
import com.example.skyeos.domain.usecase.GetCurrentMonthFixedSubscriptionCostUseCase;
import com.example.skyeos.domain.usecase.GetOverviewUseCase;
import com.example.skyeos.domain.usecase.GetProjectOptionsUseCase;
import com.example.skyeos.domain.usecase.GetRecordsForDateUseCase;
import com.example.skyeos.domain.usecase.GetRecentRecordsUseCase;
import com.example.skyeos.domain.usecase.GetTagsUseCase;
import com.example.skyeos.domain.usecase.ListCapexCostsUseCase;
import com.example.skyeos.domain.usecase.ListRecurringCostRulesUseCase;
import com.example.skyeos.domain.usecase.LifeOsUseCases;
import com.example.skyeos.domain.usecase.ProjectUseCases;
import com.example.skyeos.domain.usecase.RegisterExternalBackupUseCase;
import com.example.skyeos.domain.usecase.RecomputeMetricSnapshotUseCase;
import com.example.skyeos.domain.usecase.ReviewUseCases;
import com.example.skyeos.domain.usecase.RestoreBackupUseCase;
import com.example.skyeos.domain.usecase.SetIdealHourlyRateUseCase;
import com.example.skyeos.domain.usecase.SetCurrentMonthBasicLivingCostUseCase;
import com.example.skyeos.domain.usecase.SetCurrentMonthFixedSubscriptionCostUseCase;
import com.example.skyeos.domain.usecase.UpsertMonthlyCostBaselineUseCase;
import com.example.skyeos.domain.usecase.UpdateCapexCostUseCase;
import com.example.skyeos.domain.usecase.UpdateExpenseUseCase;
import com.example.skyeos.domain.usecase.UpdateIncomeUseCase;
import com.example.skyeos.domain.usecase.UpdateLearningUseCase;
import com.example.skyeos.domain.usecase.UpdateProjectRecordUseCase;
import com.example.skyeos.domain.usecase.UpdateRecurringCostRuleUseCase;
import com.example.skyeos.domain.usecase.UpdateTagUseCase;
import com.example.skyeos.domain.usecase.UpdateTimeLogUseCase;

public final class AppGraph {
    private static volatile AppGraph instance;

    public final LifeOsDatabase database;
    public final LifeOsWriteRepository writeRepository;
    public final LifeOsReadRepository readRepository;
    public final LifeOsReviewRepository reviewRepository;
    public final LifeOsMetricsRepository metricsRepository;
    public final LifeOsBackupRepository backupRepository;
    public final CurrentUserContext currentUserContext;
    public final ParserSettingsStore parserSettingsStore;
    public final AiApiConfigStore aiApiConfigStore;
    public final AiParseOrchestrator aiParseOrchestrator;
    public final LifeOsUseCases useCases;

    private AppGraph(Context context) {
        this.database = LifeOsDatabase.getInstance(context);
        this.currentUserContext = new SQLiteCurrentUserContext(context, database);
        this.writeRepository = new SQLiteLifeOsWriteRepository(database, currentUserContext);
        this.readRepository = new SQLiteLifeOsReadRepository(database, currentUserContext);
        this.reviewRepository = new SQLiteLifeOsReviewRepository(database, currentUserContext);
        this.metricsRepository = new SQLiteLifeOsMetricsRepository(database, currentUserContext);
        this.backupRepository = new SQLiteLifeOsBackupRepository(context, database, currentUserContext);
        this.parserSettingsStore = new ParserSettingsStore(context);
        this.aiApiConfigStore = new AiApiConfigStore(context);
        this.aiParseOrchestrator = new AiParseOrchestrator(
                new LlmApiParserEngine(aiApiConfigStore),
                new VcpParserEngine(),
                new RuleParserEngine(),
                parserSettingsStore.loadMode());
        this.useCases = new LifeOsUseCases(
                new CreateProjectUseCase(writeRepository),
                new CreateTimeLogUseCase(writeRepository),
                new CreateIncomeUseCase(writeRepository),
                new CreateExpenseUseCase(writeRepository),
                new CreateLearningUseCase(writeRepository),
                new RecomputeMetricSnapshotUseCase(metricsRepository),
                new GetMetricSnapshotUseCase(metricsRepository),
                new GetRateComparisonUseCase(metricsRepository),
                new GetIdealHourlyRateUseCase(metricsRepository),
                new SetIdealHourlyRateUseCase(metricsRepository),
                new GetCurrentMonthBasicLivingCostUseCase(metricsRepository),
                new SetCurrentMonthBasicLivingCostUseCase(metricsRepository),
                new GetCurrentMonthFixedSubscriptionCostUseCase(metricsRepository),
                new SetCurrentMonthFixedSubscriptionCostUseCase(metricsRepository),
                new GetMonthlyCostBaselineUseCase(metricsRepository),
                new UpsertMonthlyCostBaselineUseCase(metricsRepository),
                new ListRecurringCostRulesUseCase(metricsRepository),
                new CreateRecurringCostRuleUseCase(metricsRepository),
                new UpdateRecurringCostRuleUseCase(metricsRepository),
                new DeleteRecurringCostRuleUseCase(metricsRepository),
                new ListCapexCostsUseCase(metricsRepository),
                new CreateCapexCostUseCase(metricsRepository),
                new UpdateCapexCostUseCase(metricsRepository),
                new DeleteCapexCostUseCase(metricsRepository),
                new GetOverviewUseCase(readRepository),
                new GetRecentRecordsUseCase(readRepository),
                new GetRecordsForDateUseCase(readRepository),
                new GetProjectOptionsUseCase(readRepository),
                new GetTagsUseCase(readRepository),
                new CreateTagUseCase(writeRepository),
                new UpdateTimeLogUseCase(writeRepository),
                new UpdateIncomeUseCase(writeRepository),
                new UpdateExpenseUseCase(writeRepository),
                new UpdateLearningUseCase(writeRepository),
                new UpdateProjectRecordUseCase(writeRepository),
                new UpdateTagUseCase(writeRepository),
                new DeleteRecordUseCase(writeRepository),
                new DeleteProjectUseCase(writeRepository),
                new DeleteTagUseCase(writeRepository),
                new ProjectUseCases(readRepository, writeRepository),
                new ReviewUseCases(reviewRepository),
                new CreateBackupUseCase(backupRepository),
                new RegisterExternalBackupUseCase(backupRepository),
                new RestoreBackupUseCase(backupRepository),
                new GetLatestBackupUseCase(backupRepository));
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
