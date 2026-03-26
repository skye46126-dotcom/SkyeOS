package com.example.skyeos.domain.model.input;

public final class CreateExpenseInput {
    public final String occurredOn;
    public final String category;
    public final long amountCents;
    public final Integer aiAssistRatio;
    public final String note;
    public final java.util.List<com.example.skyeos.domain.model.ProjectAllocation> projectAllocations;
    public final java.util.List<String> tagIds;

    public CreateExpenseInput(String occurredOn, String category, long amountCents, Integer aiAssistRatio, String note,
            java.util.List<com.example.skyeos.domain.model.ProjectAllocation> projectAllocations,
            java.util.List<String> tagIds) {
        this.occurredOn = occurredOn;
        this.category = category;
        this.amountCents = amountCents;
        this.aiAssistRatio = aiAssistRatio;
        this.note = note;
        this.projectAllocations = projectAllocations;
        this.tagIds = tagIds;
    }
}
