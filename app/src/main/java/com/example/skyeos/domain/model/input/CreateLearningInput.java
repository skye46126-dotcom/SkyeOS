package com.example.skyeos.domain.model.input;

import com.example.skyeos.domain.model.ProjectAllocation;

import java.util.List;

public final class CreateLearningInput {
    public final String occurredOn;
    public final String content;
    public final int durationMinutes;
    public final String applicationLevel;
    public final String note;
    public final List<ProjectAllocation> projectAllocations;
    public final List<String> tagIds;

    public CreateLearningInput(
            String occurredOn,
            String content,
            int durationMinutes,
            String applicationLevel,
            String note,
            List<ProjectAllocation> projectAllocations,
            List<String> tagIds
    ) {
        this.occurredOn = occurredOn;
        this.content = content;
        this.durationMinutes = durationMinutes;
        this.applicationLevel = applicationLevel;
        this.note = note;
        this.projectAllocations = projectAllocations;
        this.tagIds = tagIds;
    }
}

