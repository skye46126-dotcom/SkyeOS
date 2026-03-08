package com.example.skyeos.domain.model;

public final class RecentRecordItem {
    public final String type;
    public final String occurredAt;
    public final String title;
    public final String detail;

    public RecentRecordItem(String type, String occurredAt, String title, String detail) {
        this.type = type;
        this.occurredAt = occurredAt;
        this.title = title;
        this.detail = detail;
    }
}

