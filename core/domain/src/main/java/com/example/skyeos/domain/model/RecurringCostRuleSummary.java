package com.example.skyeos.domain.model;

public final class RecurringCostRuleSummary {
    public final String id;
    public final String name;
    public final String category;
    public final long monthlyAmountCents;
    public final boolean necessary;
    public final String startMonth;
    public final String endMonth;
    public final boolean active;
    public final String note;

    public RecurringCostRuleSummary(
            String id,
            String name,
            String category,
            long monthlyAmountCents,
            boolean necessary,
            String startMonth,
            String endMonth,
            boolean active,
            String note
    ) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.monthlyAmountCents = monthlyAmountCents;
        this.necessary = necessary;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
        this.active = active;
        this.note = note;
    }
}
