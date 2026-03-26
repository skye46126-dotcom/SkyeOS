package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.repository.LifeOsReadRepository;

import java.util.List;

public final class GetRecentRecordsUseCase {
    private final LifeOsReadRepository repository;

    @Inject
    public GetRecentRecordsUseCase(LifeOsReadRepository repository) {
        this.repository = repository;
    }

    public List<RecentRecordItem> execute(int limit) {
        return repository.getRecentRecords(limit);
    }
}

