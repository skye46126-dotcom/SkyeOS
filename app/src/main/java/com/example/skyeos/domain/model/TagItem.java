package com.example.skyeos.domain.model;

public final class TagItem {
    public final String id;
    public final String name;
    public final String emoji;
    public final String tagGroup;
    public final String scope;
    public final boolean isSystem;
    public final boolean isActive;

    public TagItem(String id, String name, String emoji, String tagGroup, String scope, boolean isSystem,
            boolean isActive) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.tagGroup = tagGroup;
        this.scope = scope;
        this.isSystem = isSystem;
        this.isActive = isActive;
    }
}
