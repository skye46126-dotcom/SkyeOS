package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.WindowOverview;
import com.example.skyeos.domain.repository.LifeOsReadRepository;

public final class GetOverviewUseCase {
    private final LifeOsReadRepository repository;

    @Inject
    public GetOverviewUseCase(LifeOsReadRepository repository) {
        this.repository = repository;
    }

    public WindowOverview execute(String anchorDate, String windowType) {
        return repository.getOverview(anchorDate, windowType);
    }
}

