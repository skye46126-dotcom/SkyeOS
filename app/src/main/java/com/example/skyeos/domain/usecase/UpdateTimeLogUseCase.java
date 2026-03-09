package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.input.CreateTimeLogInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

public final class UpdateTimeLogUseCase {
    private final LifeOsWriteRepository repository;

    public UpdateTimeLogUseCase(LifeOsWriteRepository repository) {
        this.repository = repository;
    }

    public void execute(String timeLogId, CreateTimeLogInput input) {
        repository.updateTimeLog(timeLogId, input);
    }
}
