package com.example.skyeos.domain.model;

import java.util.List;

/**
 * Detailed view of a specific project, including ROI, timelines, and raw
 * records.
 */
public class ProjectDetail {
    public final String id;
    public final String name;
    public final String status;
    public final String startedOn;
    public final String endedOn;
    public final int score;
    public final String note;

    public final long totalTimeMinutes;
    public final long totalIncomeCents;
    public final long totalExpenseCents;
    public final long timeCostCents;
    public final long totalCostCents;
    public final long profitCents;
    public final long breakEvenIncomeCents;
    public final long benchmarkHourlyRateCents;
    public final long lastYearHourlyRateCents;
    public final long idealHourlyRateCents;

    // derived
    public final double hourlyRateYuan;
    public final double roiPerc; // (income - expense)/expense * 100

    public final List<RecentRecordItem> recentRecords;

    public ProjectDetail(String id, String name, String status, String startedOn, String endedOn, int score,
            String note,
            long totalTimeMinutes, long totalIncomeCents, long totalExpenseCents,
            long timeCostCents, long totalCostCents, long profitCents, long breakEvenIncomeCents,
            long benchmarkHourlyRateCents, long lastYearHourlyRateCents, long idealHourlyRateCents,
            double hourlyRateYuan, double roiPerc, List<RecentRecordItem> recentRecords) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.startedOn = startedOn;
        this.endedOn = endedOn;
        this.score = score;
        this.note = note;
        this.totalTimeMinutes = totalTimeMinutes;
        this.totalIncomeCents = totalIncomeCents;
        this.totalExpenseCents = totalExpenseCents;
        this.timeCostCents = timeCostCents;
        this.totalCostCents = totalCostCents;
        this.profitCents = profitCents;
        this.breakEvenIncomeCents = breakEvenIncomeCents;
        this.benchmarkHourlyRateCents = benchmarkHourlyRateCents;
        this.lastYearHourlyRateCents = lastYearHourlyRateCents;
        this.idealHourlyRateCents = idealHourlyRateCents;
        this.hourlyRateYuan = hourlyRateYuan;
        this.roiPerc = roiPerc;
        this.recentRecords = recentRecords;
    }
}
