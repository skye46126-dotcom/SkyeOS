package com.example.skyeos.domain.model;

/**
 * Represents a project's progress within a specific review period.
 */
public class ProjectProgressItem {
    public final String projectId;
    public final String projectName;
    public final long timeSpentMinutes;
    public final long incomeEarnedCents;
    public final long directExpenseCents;
    public final long allocatedStructuralCostCents;
    public final long operatingCostCents;
    public final long fullyLoadedCostCents;
    public final double hourlyRateYuan;
    public final double operatingRoiPerc;
    public final double fullyLoadedRoiPerc;

    // e.g., "positive" for high ROI, "warning" for sinkhole
    public final String evaluationStatus;

    public ProjectProgressItem(String projectId, String projectName, long timeSpentMinutes,
            long incomeEarnedCents, long directExpenseCents, long allocatedStructuralCostCents,
            long operatingCostCents, long fullyLoadedCostCents, double hourlyRateYuan,
            double operatingRoiPerc, double fullyLoadedRoiPerc, String evaluationStatus) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.timeSpentMinutes = timeSpentMinutes;
        this.incomeEarnedCents = incomeEarnedCents;
        this.directExpenseCents = directExpenseCents;
        this.allocatedStructuralCostCents = allocatedStructuralCostCents;
        this.operatingCostCents = operatingCostCents;
        this.fullyLoadedCostCents = fullyLoadedCostCents;
        this.hourlyRateYuan = hourlyRateYuan;
        this.operatingRoiPerc = operatingRoiPerc;
        this.fullyLoadedRoiPerc = fullyLoadedRoiPerc;
        this.evaluationStatus = evaluationStatus;
    }
}
