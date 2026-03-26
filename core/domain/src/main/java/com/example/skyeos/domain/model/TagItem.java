package com.example.skyeos.domain.model;

public final class TagItem {
    public final String id;
    public final String name;
    public final String emoji;
    public final String tagGroup;
    public final String scope;
    public final String parentTagId;
    public final int level;
    public final boolean isSystem;
    public final boolean isActive;

    public TagItem(String id, String name, String emoji, String tagGroup, String scope, boolean isSystem,
            boolean isActive) {
        this(id, name, emoji, tagGroup, scope, null, 1, isSystem, isActive);
    }

    public TagItem(String id, String name, String emoji, String tagGroup, String scope, String parentTagId, int level,
            boolean isSystem, boolean isActive) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.tagGroup = tagGroup;
        this.scope = scope;
        this.parentTagId = parentTagId;
        this.level = level;
        this.isSystem = isSystem;
        this.isActive = isActive;
    }
}
