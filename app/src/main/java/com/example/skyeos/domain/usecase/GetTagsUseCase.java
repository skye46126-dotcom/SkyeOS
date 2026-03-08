package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.TagItem;
import com.example.skyeos.domain.repository.LifeOsReadRepository;

import java.util.List;

public final class GetTagsUseCase {
    private final LifeOsReadRepository repository;

    public GetTagsUseCase(LifeOsReadRepository repository) {
        this.repository = repository;
    }

    public List<TagItem> execute(String scope, boolean activeOnly) {
        return repository.getTags(scope, activeOnly);
    }
}
