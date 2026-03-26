package com.example.skyeos.domain.usecase;

import com.example.skyeos.domain.model.ProjectDetail;
import com.example.skyeos.domain.model.ProjectOverview;
import com.example.skyeos.domain.model.input.CreateProjectInput;
import com.example.skyeos.domain.repository.LifeOsReadRepository;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

import java.util.List;

import javax.inject.Inject;

public class ProjectUseCases {
    private final LifeOsReadRepository readRepo;
    private final LifeOsWriteRepository writeRepo;

    @Inject
    public ProjectUseCases(LifeOsReadRepository readRepo, LifeOsWriteRepository writeRepo) {
        this.readRepo = readRepo;
        this.writeRepo = writeRepo;
    }

    public List<ProjectOverview> getProjects(String status) {
        return readRepo.getProjects(status);
    }

    public ProjectDetail getProjectDetail(String projectId) {
        return readRepo.getProjectDetail(projectId);
    }

    public void updateProject(String projectId, String status, int score, String note, String endedOn) {
        // e.g. status "done" -> set endedOn
        writeRepo.updateProject(projectId, status, score, note, endedOn);
    }

    public void updateProjectRecord(String projectId, CreateProjectInput input) {
        writeRepo.updateProjectRecord(projectId, input);
    }

    public void deleteProject(String projectId) {
        writeRepo.deleteProject(projectId);
    }
}
