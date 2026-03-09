package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.repository.LifeOsReadRepository;

import java.util.List;

public final class GetRecordsForDateUseCase {
    private final LifeOsReadRepository repository;

    public GetRecordsForDateUseCase(LifeOsReadRepository repository) {
        this.repository = repository;
    }

    public List<RecentRecordItem> execute(String anchorDate, int limit) {
        return repository.getRecordsForDate(anchorDate, limit);
    }
}
