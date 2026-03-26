package com.example.skyeos.domain.model.input;

import com.example.skyeos.domain.model.ProjectAllocation;

import java.util.List;

public final class CreateLearningInput {
    public final String occurredOn;
    public final String startedAt;
    public final String endedAt;
    public final String content;
    public final int durationMinutes;
    public final Integer efficiencyScore;
    public final String applicationLevel;
    public final Integer aiAssistRatio;
    public final String note;
    public final List<ProjectAllocation> projectAllocations;
    public final List<String> tagIds;

    public CreateLearningInput(
            String occurredOn,
            String startedAt,
            String endedAt,
            String content,
            int durationMinutes,
            Integer efficiencyScore,
            String applicationLevel,
            Integer aiAssistRatio,
            String note,
            List<ProjectAllocation> projectAllocations,
            List<String> tagIds
    ) {
        this.occurredOn = occurredOn;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.content = content;
        this.durationMinutes = durationMinutes;
        this.efficiencyScore = efficiencyScore;
        this.applicationLevel = applicationLevel;
        this.aiAssistRatio = aiAssistRatio;
        this.note = note;
        this.projectAllocations = projectAllocations;
        this.tagIds = tagIds;
    }
}
