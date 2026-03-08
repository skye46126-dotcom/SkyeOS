package com.example.skyeos.domain.usecase;

public final class LifeOsUseCases {
    public final CreateProjectUseCase createProject;
    public final CreateTimeLogUseCase createTimeLog;
    public final CreateIncomeUseCase createIncome;
    public final CreateExpenseUseCase createExpense;
    public final CreateLearningUseCase createLearning;
    public final RecomputeMetricSnapshotUseCase recomputeMetricSnapshot;
    public final GetMetricSnapshotUseCase getMetricSnapshot;
    public final GetIdealHourlyRateUseCase getIdealHourlyRate;
    public final SetIdealHourlyRateUseCase setIdealHourlyRate;
    public final GetOverviewUseCase getOverview;
    public final GetRecentRecordsUseCase getRecentRecords;
    public final GetProjectOptionsUseCase getProjectOptions;
    public final GetTagsUseCase getTags;
    public final CreateTagUseCase createTag;
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
            GetIdealHourlyRateUseCase getIdealHourlyRate,
            SetIdealHourlyRateUseCase setIdealHourlyRate,
            GetOverviewUseCase getOverview,
            GetRecentRecordsUseCase getRecentRecords,
            GetProjectOptionsUseCase getProjectOptions,
            GetTagsUseCase getTags,
            CreateTagUseCase createTag,
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
        this.getIdealHourlyRate = getIdealHourlyRate;
        this.setIdealHourlyRate = setIdealHourlyRate;
        this.getOverview = getOverview;
        this.getRecentRecords = getRecentRecords;
        this.getProjectOptions = getProjectOptions;
        this.getTags = getTags;
        this.createTag = createTag;
        this.projectUseCases = projectUseCases;
        this.reviewUseCases = reviewUseCases;
        this.createBackup = createBackup;
        this.registerExternalBackup = registerExternalBackup;
        this.restoreBackup = restoreBackup;
        this.getLatestBackup = getLatestBackup;
    }
}
