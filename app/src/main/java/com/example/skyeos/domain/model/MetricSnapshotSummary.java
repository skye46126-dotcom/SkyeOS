package com.example.skyeos.domain.model;

public final class MetricSnapshotSummary {
    public final String id;
    public final String snapshotDate;
    public final String windowType;
    public final Long hourlyRateCents;
    public final Long timeDebtCents;
    public final Double passiveCoverRatio;
    public final Long freedomCents;
    public final Long totalIncomeCents;
    public final Long totalExpenseCents;
    public final Long totalWorkMinutes;
    public final String generatedAt;

    public MetricSnapshotSummary(
            String id,
            String snapshotDate,
            String windowType,
            Long hourlyRateCents,
            Long timeDebtCents,
            Double passiveCoverRatio,
            Long freedomCents,
            Long totalIncomeCents,
            Long totalExpenseCents,
            Long totalWorkMinutes,
            String generatedAt
    ) {
        this.id = id;
        this.snapshotDate = snapshotDate;
        this.windowType = windowType;
        this.hourlyRateCents = hourlyRateCents;
        this.timeDebtCents = timeDebtCents;
        this.passiveCoverRatio = passiveCoverRatio;
        this.freedomCents = freedomCents;
        this.totalIncomeCents = totalIncomeCents;
        this.totalExpenseCents = totalExpenseCents;
        this.totalWorkMinutes = totalWorkMinutes;
        this.generatedAt = generatedAt;
    }
}

