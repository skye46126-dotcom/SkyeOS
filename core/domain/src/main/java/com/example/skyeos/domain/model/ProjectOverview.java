package com.example.skyeos.domain.model;

/**
 * Summary of a single project for list views.
 */
public class ProjectOverview {
    public final String id;
    public final String name;
    public final String status; // active, done, paused
    public final int score; // 1 to 5 priority
    public final long totalTimeMinutes;
    public final long totalIncomeCents;

    public ProjectOverview(String id, String name, String status, int score, long totalTimeMinutes,
            long totalIncomeCents) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.score = score;
        this.totalTimeMinutes = totalTimeMinutes;
        this.totalIncomeCents = totalIncomeCents;
    }
}
