package com.example.skyeos.domain.model;

public final class ProjectAllocation {
    public final String projectId;
    public final double weightRatio;

    public ProjectAllocation(String projectId, double weightRatio) {
        this.projectId = projectId;
        this.weightRatio = weightRatio;
    }
}

