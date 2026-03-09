package com.example.skyeos.domain.model;

public final class RateComparisonSummary {
    public final String anchorDate;
    public final String windowType;
    public final long idealHourlyRateCents;
    public final Long previousYearAverageHourlyRateCents;
    public final Long actualHourlyRateCents;
    public final Long previousYearIncomeCents;
    public final Long previousYearWorkMinutes;
    public final Long currentIncomeCents;
    public final Long currentWorkMinutes;

    public RateComparisonSummary(
            String anchorDate,
            String windowType,
            long idealHourlyRateCents,
            Long previousYearAverageHourlyRateCents,
            Long actualHourlyRateCents,
            Long previousYearIncomeCents,
            Long previousYearWorkMinutes,
            Long currentIncomeCents,
            Long currentWorkMinutes
    ) {
        this.anchorDate = anchorDate;
        this.windowType = windowType;
        this.idealHourlyRateCents = idealHourlyRateCents;
        this.previousYearAverageHourlyRateCents = previousYearAverageHourlyRateCents;
        this.actualHourlyRateCents = actualHourlyRateCents;
        this.previousYearIncomeCents = previousYearIncomeCents;
        this.previousYearWorkMinutes = previousYearWorkMinutes;
        this.currentIncomeCents = currentIncomeCents;
        this.currentWorkMinutes = currentWorkMinutes;
    }
}
