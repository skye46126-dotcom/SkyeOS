package com.example.skyeos.domain.model.input;

public final class CreateProjectInput {
    public final String name;
    public final String startedOn;
    public final String status;
    public final Integer aiEnableRatio;
    public final Integer score;
    public final String note;

    public CreateProjectInput(
            String name,
            String startedOn,
            String status,
            Integer aiEnableRatio,
            Integer score,
            String note
    ) {
        this.name = name;
        this.startedOn = startedOn;
        this.status = status;
        this.aiEnableRatio = aiEnableRatio;
        this.score = score;
        this.note = note;
    }
}

