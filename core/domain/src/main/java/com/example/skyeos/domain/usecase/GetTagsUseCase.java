package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.TagItem;
import com.example.skyeos.domain.repository.LifeOsReadRepository;

import java.util.List;

public final class GetTagsUseCase {
    private final LifeOsReadRepository repository;

    @Inject
    public GetTagsUseCase(LifeOsReadRepository repository) {
        this.repository = repository;
    }

    public List<TagItem> execute(String scope, boolean activeOnly) {
        return repository.getTags(scope, activeOnly);
    }
}
