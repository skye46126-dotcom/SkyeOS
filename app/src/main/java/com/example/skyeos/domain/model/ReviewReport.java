package com.example.skyeos.domain.model;

import java.util.List;

/**
 * Encapsulates the review data for a specific period (Daily, Weekly, Monthly).
 */
public class ReviewReport {
    public final String periodName; // e.g., "Today", "This Week"
    public final String aiSummary; // Intelligent hero summary

    // Core Metrics
    public final long totalTimeMinutes;
    public final long totalWorkMinutes;
    public final long totalIncomeCents;
    public final long totalExpenseCents;
    public final long prevIncomeCents;
    public final long prevExpenseCents;
    public final long prevWorkMinutes;
    public final Double incomeChangeRatio; // nullable if previous is 0
    public final Double expenseChangeRatio; // nullable if previous is 0
    public final Double workChangeRatio; // nullable if previous is 0
    public final Long actualHourlyRateCents;
    public final long idealHourlyRateCents;
    public final Long timeDebtCents;
    public final Double passiveCoverRatio;
    public final Double freedomDelta; // nullable: no baseline => null
    public final Double currentFreedomPercentage; // nullable: unavailable => null
    public final Double aiAssistRate; // nullable: unavailable => null
    public final Double workEfficiencyAvg; // nullable: unavailable => null
    public final Double learningEfficiencyAvg; // nullable: unavailable => null

    // Time Allocation
    public final List<TimeCategoryAllocation> timeAllocations; // e.g., { "Learning": 15, "Project A": 40 }

    // Projects Leaderboard & Progress
    public final List<ProjectProgressItem> topProjects; // High ROI projects this period
    public final List<ProjectProgressItem> sinkholeProjects; // High time, low/zero ROI projects

    // Key Events
    public final List<RecentRecordItem> keyEvents; // Filtered top events
    public final List<RecentRecordItem> incomeHistory; // Income records in current window
    public final List<RecentRecordItem> historyRecords; // Unified records in current window
    public final List<TagMetric> timeTagMetrics; // by minutes
    public final List<TagMetric> expenseTagMetrics; // by amount cents

    public ReviewReport(String periodName, String aiSummary, long totalTimeMinutes, long totalWorkMinutes,
            long totalIncomeCents,
            long totalExpenseCents, long prevIncomeCents, long prevExpenseCents, long prevWorkMinutes,
            Double incomeChangeRatio, Double expenseChangeRatio, Double workChangeRatio, Long actualHourlyRateCents,
            long idealHourlyRateCents, Long timeDebtCents,
            Double passiveCoverRatio, Double freedomDelta, Double currentFreedomPercentage,
            Double aiAssistRate, Double workEfficiencyAvg, Double learningEfficiencyAvg,
            List<TimeCategoryAllocation> timeAllocations, List<ProjectProgressItem> topProjects,
            List<ProjectProgressItem> sinkholeProjects, List<RecentRecordItem> keyEvents,
            List<RecentRecordItem> incomeHistory, List<RecentRecordItem> historyRecords,
            List<TagMetric> timeTagMetrics, List<TagMetric> expenseTagMetrics) {
        this.periodName = periodName;
        this.aiSummary = aiSummary;
        this.totalTimeMinutes = totalTimeMinutes;
        this.totalWorkMinutes = totalWorkMinutes;
        this.totalIncomeCents = totalIncomeCents;
        this.totalExpenseCents = totalExpenseCents;
        this.prevIncomeCents = prevIncomeCents;
        this.prevExpenseCents = prevExpenseCents;
        this.prevWorkMinutes = prevWorkMinutes;
        this.incomeChangeRatio = incomeChangeRatio;
        this.expenseChangeRatio = expenseChangeRatio;
        this.workChangeRatio = workChangeRatio;
        this.actualHourlyRateCents = actualHourlyRateCents;
        this.idealHourlyRateCents = idealHourlyRateCents;
        this.timeDebtCents = timeDebtCents;
        this.passiveCoverRatio = passiveCoverRatio;
        this.freedomDelta = freedomDelta;
        this.currentFreedomPercentage = currentFreedomPercentage;
        this.aiAssistRate = aiAssistRate;
        this.workEfficiencyAvg = workEfficiencyAvg;
        this.learningEfficiencyAvg = learningEfficiencyAvg;
        this.timeAllocations = timeAllocations;
        this.topProjects = topProjects;
        this.sinkholeProjects = sinkholeProjects;
        this.keyEvents = keyEvents;
        this.incomeHistory = incomeHistory;
        this.historyRecords = historyRecords;
        this.timeTagMetrics = timeTagMetrics;
        this.expenseTagMetrics = expenseTagMetrics;
    }

    public static class TimeCategoryAllocation {
        public final String categoryName;
        public final long minutes;
        public final double percentage;

        public TimeCategoryAllocation(String categoryName, long minutes, double percentage) {
            this.categoryName = categoryName;
            this.minutes = minutes;
            this.percentage = percentage;
        }
    }

    public static class TagMetric {
        public final String tagName;
        public final String emoji;
        public final long value;
        public final double percentage;

        public TagMetric(String tagName, String emoji, long value, double percentage) {
            this.tagName = tagName;
            this.emoji = emoji;
            this.value = value;
            this.percentage = percentage;
        }
    }
}
