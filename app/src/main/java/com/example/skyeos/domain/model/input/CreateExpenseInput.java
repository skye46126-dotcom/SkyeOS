package com.example.skyeos.domain.model.input;

public final class CreateExpenseInput {
    public final String occurredOn;
    public final String category;
    public final long amountCents;
    public final String note;

    public CreateExpenseInput(String occurredOn, String category, long amountCents, String note) {
        this.occurredOn = occurredOn;
        this.category = category;
        this.amountCents = amountCents;
        this.note = note;
    }
}

