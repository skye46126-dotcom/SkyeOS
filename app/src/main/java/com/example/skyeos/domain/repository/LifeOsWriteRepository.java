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

    void updateProject(String projectId, String status, int score, String note, String endedOn);
}
