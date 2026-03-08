package com.example.skyeos.domain.model.input;

import com.example.skyeos.domain.model.ProjectAllocation;

import java.util.List;

public final class CreateIncomeInput {
    public final String occurredOn;
    public final String sourceName;
    public final String type;
    public final long amountCents;
    public final boolean isPassive;
    public final String note;
    public final List<ProjectAllocation> projectAllocations;
    public final List<String> tagIds;

    public CreateIncomeInput(
            String occurredOn,
            String sourceName,
            String type,
            long amountCents,
            boolean isPassive,
            String note,
            List<ProjectAllocation> projectAllocations,
            List<String> tagIds
    ) {
        this.occurredOn = occurredOn;
        this.sourceName = sourceName;
        this.type = type;
        this.amountCents = amountCents;
        this.isPassive = isPassive;
        this.note = note;
        this.projectAllocations = projectAllocations;
        this.tagIds = tagIds;
    }
}
