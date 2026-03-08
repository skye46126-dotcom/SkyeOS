package com.example.skyeos.domain.model.input;

import com.example.skyeos.domain.model.ProjectAllocation;

import java.util.List;

public final class CreateTimeLogInput {
    public final String startedAt;
    public final String endedAt;
    public final String category;
    public final Integer efficiencyScore;
    public final Integer valueScore;
    public final Integer stateScore;
    public final Integer aiAssistRatio;
    public final String note;
    public final List<ProjectAllocation> projectAllocations;
    public final List<String> tagIds;

    public CreateTimeLogInput(
            String startedAt,
            String endedAt,
            String category,
            Integer efficiencyScore,
            Integer valueScore,
            Integer stateScore,
            Integer aiAssistRatio,
            String note,
            List<ProjectAllocation> projectAllocations,
            List<String> tagIds
    ) {
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.category = category;
        this.efficiencyScore = efficiencyScore;
        this.valueScore = valueScore;
        this.stateScore = stateScore;
        this.aiAssistRatio = aiAssistRatio;
        this.note = note;
        this.projectAllocations = projectAllocations;
        this.tagIds = tagIds;
    }
}
