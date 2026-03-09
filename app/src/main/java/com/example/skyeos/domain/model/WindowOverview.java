package com.example.skyeos.domain.model;

public final class WindowOverview {
    public final String windowType;
    public final String anchorDate;
    public final String startDate;
    public final String endDate;
    public final long totalIncomeCents;
    public final long actualExpenseCents;
    public final long structuralExpenseCents;
    public final long totalExpenseCents;
    public final long netIncomeCents;
    public final long totalWorkMinutes;
    public final long totalTimeMinutes;
    public final long totalLearningMinutes;
    public final double publicTimeRatio;
    public final double publicIncomeRatio;
    public final double publicLearningRatio;

    public WindowOverview(
            String windowType,
            String anchorDate,
            String startDate,
            String endDate,
            long totalIncomeCents,
            long actualExpenseCents,
            long structuralExpenseCents,
            long totalExpenseCents,
            long netIncomeCents,
            long totalWorkMinutes,
            long totalTimeMinutes,
            long totalLearningMinutes,
            double publicTimeRatio,
            double publicIncomeRatio,
            double publicLearningRatio
    ) {
        this.windowType = windowType;
        this.anchorDate = anchorDate;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalIncomeCents = totalIncomeCents;
        this.actualExpenseCents = actualExpenseCents;
        this.structuralExpenseCents = structuralExpenseCents;
        this.totalExpenseCents = totalExpenseCents;
        this.netIncomeCents = netIncomeCents;
        this.totalWorkMinutes = totalWorkMinutes;
        this.totalTimeMinutes = totalTimeMinutes;
        this.totalLearningMinutes = totalLearningMinutes;
        this.publicTimeRatio = publicTimeRatio;
        this.publicIncomeRatio = publicIncomeRatio;
        this.publicLearningRatio = publicLearningRatio;
    }
}
