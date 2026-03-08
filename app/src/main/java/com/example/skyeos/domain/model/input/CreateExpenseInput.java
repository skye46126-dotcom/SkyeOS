package com.example.skyeos.domain.model.input;

public final class CreateExpenseInput {
    public final String occurredOn;
    public final String category;
    public final long amountCents;
    public final String note;
    public final java.util.List<String> tagIds;

    public CreateExpenseInput(String occurredOn, String category, long amountCents, String note,
            java.util.List<String> tagIds) {
        this.occurredOn = occurredOn;
        this.category = category;
        this.amountCents = amountCents;
        this.note = note;
        this.tagIds = tagIds;
    }
}
