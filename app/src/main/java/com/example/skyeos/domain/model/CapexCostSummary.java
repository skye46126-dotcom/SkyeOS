package com.example.skyeos.domain.model;

public final class CapexCostSummary {
    public final String id;
    public final String name;
    public final String purchaseDate;
    public final long purchaseAmountCents;
    public final long monthlyAmortizedCents;
    public final String amortizationStartMonth;
    public final String amortizationEndMonth;
    public final boolean active;
    public final String note;

    public CapexCostSummary(
            String id,
            String name,
            String purchaseDate,
            long purchaseAmountCents,
            long monthlyAmortizedCents,
            String amortizationStartMonth,
            String amortizationEndMonth,
            boolean active,
            String note
    ) {
        this.id = id;
        this.name = name;
        this.purchaseDate = purchaseDate;
        this.purchaseAmountCents = purchaseAmountCents;
        this.monthlyAmortizedCents = monthlyAmortizedCents;
        this.amortizationStartMonth = amortizationStartMonth;
        this.amortizationEndMonth = amortizationEndMonth;
        this.active = active;
        this.note = note;
    }
}
