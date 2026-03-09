package com.example.skyeos.domain.model;

public final class MonthlyCostBaseline {
    public final String month;
    public final long basicLivingCents;
    public final long fixedSubscriptionCents;

    public MonthlyCostBaseline(String month, long basicLivingCents, long fixedSubscriptionCents) {
        this.month = month;
        this.basicLivingCents = basicLivingCents;
        this.fixedSubscriptionCents = fixedSubscriptionCents;
    }
}
