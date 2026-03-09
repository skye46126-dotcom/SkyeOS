package com.example.skyeos.domain.repository;

import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.model.input.CreateProjectInput;
import com.example.skyeos.domain.model.input.CreateTimeLogInput;

public interface LifeOsWriteRepository {
    String createProject(CreateProjectInput input);

    String createTimeLog(CreateTimeLogInput input);

    String createIncome(CreateIncomeInput input);

    String createExpense(CreateExpenseInput input);

    String createLearning(CreateLearningInput input);

    String createTag(String name, String emoji, String tagGroup, String scope);

    void updateProjectRecord(String projectId, CreateProjectInput input);

    void updateProject(String projectId, String status, int score, String note, String endedOn);

    void updateTimeLog(String timeLogId, CreateTimeLogInput input);

    void updateIncome(String incomeId, CreateIncomeInput input);

    void updateExpense(String expenseId, CreateExpenseInput input);

    void updateLearning(String learningId, CreateLearningInput input);

    void updateTag(String tagId, String name, String emoji, String tagGroup, String scope, boolean active);

    void deleteRecord(String type, String recordId);

    void deleteProject(String projectId);

    void deleteTag(String tagId);
}
