package com.example.skyeos.domain.model;

/**
 * Represents a project's progress within a specific review period.
 */
public class ProjectProgressItem {
    public final String projectId;
    public final String projectName;
    public final long timeSpentMinutes;
    public final long incomeEarnedCents;
    public final double hourlyRateYuan;

    // e.g., "positive" for high ROI, "warning" for sinkhole
    public final String evaluationStatus;

    public ProjectProgressItem(String projectId, String projectName, long timeSpentMinutes,
            long incomeEarnedCents, double hourlyRateYuan, String evaluationStatus) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.timeSpentMinutes = timeSpentMinutes;
        this.incomeEarnedCents = incomeEarnedCents;
        this.hourlyRateYuan = hourlyRateYuan;
        this.evaluationStatus = evaluationStatus;
    }
}
