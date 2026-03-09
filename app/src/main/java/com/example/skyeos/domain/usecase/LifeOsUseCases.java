package com.example.skyeos.domain.usecase;

public final class LifeOsUseCases {
    public final CreateProjectUseCase createProject;
    public final CreateTimeLogUseCase createTimeLog;
    public final CreateIncomeUseCase createIncome;
    public final CreateExpenseUseCase createExpense;
    public final CreateLearningUseCase createLearning;
    public final RecomputeMetricSnapshotUseCase recomputeMetricSnapshot;
    public final GetMetricSnapshotUseCase getMetricSnapshot;
    public final GetRateComparisonUseCase getRateComparison;
    public final GetIdealHourlyRateUseCase getIdealHourlyRate;
    public final SetIdealHourlyRateUseCase setIdealHourlyRate;
    public final GetCurrentMonthBasicLivingCostUseCase getCurrentMonthBasicLivingCost;
    public final SetCurrentMonthBasicLivingCostUseCase setCurrentMonthBasicLivingCost;
    public final GetCurrentMonthFixedSubscriptionCostUseCase getCurrentMonthFixedSubscriptionCost;
    public final SetCurrentMonthFixedSubscriptionCostUseCase setCurrentMonthFixedSubscriptionCost;
    public final GetMonthlyCostBaselineUseCase getMonthlyCostBaseline;
    public final UpsertMonthlyCostBaselineUseCase upsertMonthlyCostBaseline;
    public final ListRecurringCostRulesUseCase listRecurringCostRules;
    public final CreateRecurringCostRuleUseCase createRecurringCostRule;
    public final UpdateRecurringCostRuleUseCase updateRecurringCostRule;
    public final DeleteRecurringCostRuleUseCase deleteRecurringCostRule;
    public final ListCapexCostsUseCase listCapexCosts;
    public final CreateCapexCostUseCase createCapexCost;
    public final UpdateCapexCostUseCase updateCapexCost;
    public final DeleteCapexCostUseCase deleteCapexCost;
    public final GetOverviewUseCase getOverview;
    public final GetRecentRecordsUseCase getRecentRecords;
    public final GetRecordsForDateUseCase getRecordsForDate;
    public final GetProjectOptionsUseCase getProjectOptions;
    public final GetTagsUseCase getTags;
    public final CreateTagUseCase createTag;
    public final UpdateTimeLogUseCase updateTimeLog;
    public final UpdateIncomeUseCase updateIncome;
    public final UpdateExpenseUseCase updateExpense;
    public final UpdateLearningUseCase updateLearning;
    public final UpdateProjectRecordUseCase updateProjectRecord;
    public final UpdateTagUseCase updateTag;
    public final DeleteRecordUseCase deleteRecord;
    public final DeleteProjectUseCase deleteProject;
    public final DeleteTagUseCase deleteTag;
    public final ProjectUseCases projectUseCases;
    public final ReviewUseCases reviewUseCases;
    public final CreateBackupUseCase createBackup;
    public final RegisterExternalBackupUseCase registerExternalBackup;
    public final RestoreBackupUseCase restoreBackup;
    public final GetLatestBackupUseCase getLatestBackup;

    public LifeOsUseCases(
            CreateProjectUseCase createProject,
            CreateTimeLogUseCase createTimeLog,
            CreateIncomeUseCase createIncome,
            CreateExpenseUseCase createExpense,
            CreateLearningUseCase createLearning,
            RecomputeMetricSnapshotUseCase recomputeMetricSnapshot,
            GetMetricSnapshotUseCase getMetricSnapshot,
            GetRateComparisonUseCase getRateComparison,
            GetIdealHourlyRateUseCase getIdealHourlyRate,
            SetIdealHourlyRateUseCase setIdealHourlyRate,
            GetCurrentMonthBasicLivingCostUseCase getCurrentMonthBasicLivingCost,
            SetCurrentMonthBasicLivingCostUseCase setCurrentMonthBasicLivingCost,
            GetCurrentMonthFixedSubscriptionCostUseCase getCurrentMonthFixedSubscriptionCost,
            SetCurrentMonthFixedSubscriptionCostUseCase setCurrentMonthFixedSubscriptionCost,
            GetMonthlyCostBaselineUseCase getMonthlyCostBaseline,
            UpsertMonthlyCostBaselineUseCase upsertMonthlyCostBaseline,
            ListRecurringCostRulesUseCase listRecurringCostRules,
            CreateRecurringCostRuleUseCase createRecurringCostRule,
            UpdateRecurringCostRuleUseCase updateRecurringCostRule,
            DeleteRecurringCostRuleUseCase deleteRecurringCostRule,
            ListCapexCostsUseCase listCapexCosts,
            CreateCapexCostUseCase createCapexCost,
            UpdateCapexCostUseCase updateCapexCost,
            DeleteCapexCostUseCase deleteCapexCost,
            GetOverviewUseCase getOverview,
            GetRecentRecordsUseCase getRecentRecords,
            GetRecordsForDateUseCase getRecordsForDate,
            GetProjectOptionsUseCase getProjectOptions,
            GetTagsUseCase getTags,
            CreateTagUseCase createTag,
            UpdateTimeLogUseCase updateTimeLog,
            UpdateIncomeUseCase updateIncome,
            UpdateExpenseUseCase updateExpense,
            UpdateLearningUseCase updateLearning,
            UpdateProjectRecordUseCase updateProjectRecord,
            UpdateTagUseCase updateTag,
            DeleteRecordUseCase deleteRecord,
            DeleteProjectUseCase deleteProject,
            DeleteTagUseCase deleteTag,
            ProjectUseCases projectUseCases,
            ReviewUseCases reviewUseCases,
            CreateBackupUseCase createBackup,
            RegisterExternalBackupUseCase registerExternalBackup,
            RestoreBackupUseCase restoreBackup,
            GetLatestBackupUseCase getLatestBackup) {
        this.createProject = createProject;
        this.createTimeLog = createTimeLog;
        this.createIncome = createIncome;
        this.createExpense = createExpense;
        this.createLearning = createLearning;
        this.recomputeMetricSnapshot = recomputeMetricSnapshot;
        this.getMetricSnapshot = getMetricSnapshot;
        this.getRateComparison = getRateComparison;
        this.getIdealHourlyRate = getIdealHourlyRate;
        this.setIdealHourlyRate = setIdealHourlyRate;
        this.getCurrentMonthBasicLivingCost = getCurrentMonthBasicLivingCost;
        this.setCurrentMonthBasicLivingCost = setCurrentMonthBasicLivingCost;
        this.getCurrentMonthFixedSubscriptionCost = getCurrentMonthFixedSubscriptionCost;
        this.setCurrentMonthFixedSubscriptionCost = setCurrentMonthFixedSubscriptionCost;
        this.getMonthlyCostBaseline = getMonthlyCostBaseline;
        this.upsertMonthlyCostBaseline = upsertMonthlyCostBaseline;
        this.listRecurringCostRules = listRecurringCostRules;
        this.createRecurringCostRule = createRecurringCostRule;
        this.updateRecurringCostRule = updateRecurringCostRule;
        this.deleteRecurringCostRule = deleteRecurringCostRule;
        this.listCapexCosts = listCapexCosts;
        this.createCapexCost = createCapexCost;
        this.updateCapexCost = updateCapexCost;
        this.deleteCapexCost = deleteCapexCost;
        this.getOverview = getOverview;
        this.getRecentRecords = getRecentRecords;
        this.getRecordsForDate = getRecordsForDate;
        this.getProjectOptions = getProjectOptions;
        this.getTags = getTags;
        this.createTag = createTag;
        this.updateTimeLog = updateTimeLog;
        this.updateIncome = updateIncome;
        this.updateExpense = updateExpense;
        this.updateLearning = updateLearning;
        this.updateProjectRecord = updateProjectRecord;
        this.updateTag = updateTag;
        this.deleteRecord = deleteRecord;
        this.deleteProject = deleteProject;
        this.deleteTag = deleteTag;
        this.projectUseCases = projectUseCases;
        this.reviewUseCases = reviewUseCases;
        this.createBackup = createBackup;
        this.registerExternalBackup = registerExternalBackup;
        this.restoreBackup = restoreBackup;
        this.getLatestBackup = getLatestBackup;
    }
}
