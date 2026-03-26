package com.example.skyeos.data.repository;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.text.TextUtils;

import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.domain.model.ProjectAllocation;
import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.model.input.CreateProjectInput;
import com.example.skyeos.domain.model.input.CreateTimeLogInput;
import com.example.skyeos.domain.repository.LifeOsWriteRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

public final class SQLiteLifeOsWriteRepository implements LifeOsWriteRepository {
    private static final Set<String> PROJECT_STATUS = setOf("active", "paused", "done");
    private static final Set<String> INCOME_TYPE = setOf("salary", "project", "investment", "system", "other");
    private static final Set<String> EXPENSE_CATEGORY = setOf("necessary", "experience", "subscription", "investment");
    private static final Set<String> LEARNING_LEVEL = setOf("input", "applied", "result");
    private static final Set<String> TAG_SCOPE = setOf("global", "time", "project", "income", "expense", "learning",
            "investment");

    private final LifeOsDatabase database;
    private final CurrentUserContext userContext;

    @Inject
    public SQLiteLifeOsWriteRepository(LifeOsDatabase database, CurrentUserContext userContext) {
        this.database = database;
        this.userContext = userContext;
    }

    @Override
    public String createProject(CreateProjectInput input) {
        requireNotBlank(input.name, "project.name");
        requireNotBlank(input.startedOn, "project.startedOn");
        String status = normalizeOrDefault(input.status, "active");
        requireContains(PROJECT_STATUS, status, "project.status");
        if (input.aiEnableRatio != null && (input.aiEnableRatio < 0 || input.aiEnableRatio > 100)) {
            throw new IllegalArgumentException("project.aiEnableRatio must be 0-100");
        }
        if (input.score != null && (input.score < 1 || input.score > 10)) {
            throw new IllegalArgumentException("project.score must be 1-10");
        }

        List<String> tagIds = safeList(input.tagIds);
        String id = randomId();
        String now = nowIso();
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("name", input.name.trim());
            values.put("status", status);
            values.put("status_id", requireDimensionId(db, "dim_project_status", status, "project.status"));
            values.put("started_on", input.startedOn.trim());
            if (input.aiEnableRatio != null) {
                values.put("ai_enable_ratio", input.aiEnableRatio);
            }
            if (input.score != null) {
                values.put("score", input.score);
            }
            values.put("note", nullSafe(input.note));
            values.put("owner_user_id", userId);
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertOrThrow("project", null, values);

            ContentValues ownerMember = new ContentValues();
            ownerMember.put("project_id", id);
            ownerMember.put("user_id", userId);
            ownerMember.put("role", "owner");
            ownerMember.put("created_at", now);
            db.insertWithOnConflict("project_member", null, ownerMember, SQLiteDatabase.CONFLICT_IGNORE);
            replaceTagLinks(db, "project", id, tagIds, userId, now);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return id;
    }

    @Override
    public String createTimeLog(CreateTimeLogInput input) {
        requireNotBlank(input.startedAt, "timeLog.startedAt");
        requireNotBlank(input.endedAt, "timeLog.endedAt");
        String category = normalizeTimeCategory(input.category);
        validateScore(input.efficiencyScore, "timeLog.efficiencyScore");
        validateScore(input.valueScore, "timeLog.valueScore");
        validateScore(input.stateScore, "timeLog.stateScore");
        validatePercentage(input.aiAssistRatio, "timeLog.aiAssistRatio");
        validateWorkLearningRequiredFields(category, input.valueScore, input.stateScore, input.aiAssistRatio);
        validateAllocations(input.projectAllocations, "timeLog.projectAllocations");

        long durationMinutes = computeDurationMinutes(input.startedAt, input.endedAt);
        boolean hasAllocationsInput = input.projectAllocations != null;
        boolean hasTagsInput = input.tagIds != null;
        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        boolean isPublicPool = allocations.isEmpty();
        String userId = userContext.requireCurrentUserId();
        if (hasAllocationsInput) {
            validateProjectAccess(database.readableDb(), allocations, userId, "timeLog.projectAllocations");
        }

        String id = randomId();
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("started_at", input.startedAt.trim());
            values.put("ended_at", input.endedAt.trim());
            values.put("duration_minutes", durationMinutes);
            values.put("category", category);
            values.put("category_id", resolveTimeCategoryId(db, category));
            if (input.efficiencyScore != null) {
                values.put("efficiency_score", input.efficiencyScore);
            }
            if (input.valueScore != null) {
                values.put("value_score", input.valueScore);
            }
            if (input.stateScore != null) {
                values.put("state_score", input.stateScore);
            }
            if (input.aiAssistRatio != null) {
                values.put("ai_assist_ratio", input.aiAssistRatio);
            }
            values.put("note", nullSafe(input.note));
            values.put("source", "manual");
            values.put("is_public_pool", toInt(isPublicPool));
            values.put("owner_user_id", userId);
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertOrThrow("time_log", null, values);
            replaceProjectLinks(db, "time_log", id, allocations, userId, now);
            replaceTagLinks(db, "time_log", id, tagIds, userId, now);

            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public String createIncome(CreateIncomeInput input) {
        requireNotBlank(input.occurredOn, "income.occurredOn");
        requireNotBlank(input.sourceName, "income.sourceName");
        String type = normalizeOrDefault(input.type, "other");
        requireContains(INCOME_TYPE, type, "income.type");
        if (input.amountCents < 0) {
            throw new IllegalArgumentException("income.amountCents must be >= 0");
        }
        validatePercentage(input.aiAssistRatio, "income.aiAssistRatio");
        validateAllocations(input.projectAllocations, "income.projectAllocations");

        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        boolean isPublicPool = allocations.isEmpty();
        String userId = userContext.requireCurrentUserId();
        validateProjectAccess(database.readableDb(), allocations, userId, "income.projectAllocations");
        String id = randomId();
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("occurred_on", input.occurredOn.trim());
            values.put("source_name", input.sourceName.trim());
            values.put("type", type);
            values.put("type_id", requireDimensionId(db, "dim_income_type", type, "income.type"));
            values.put("amount_cents", input.amountCents);
            values.put("is_passive", toInt(input.isPassive));
            if (input.aiAssistRatio != null) {
                values.put("ai_assist_ratio", input.aiAssistRatio);
            }
            values.put("note", nullSafe(input.note));
            values.put("source", "manual");
            values.put("is_public_pool", toInt(isPublicPool));
            values.put("owner_user_id", userId);
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertOrThrow("income", null, values);
            replaceProjectLinks(db, "income", id, allocations, userId, now);
            replaceTagLinks(db, "income", id, tagIds, userId, now);

            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public String createExpense(CreateExpenseInput input) {
        requireNotBlank(input.occurredOn, "expense.occurredOn");
        String category = normalizeOrDefault(input.category, "necessary");
        requireContains(EXPENSE_CATEGORY, category, "expense.category");
        if (input.amountCents <= 0) {
            throw new IllegalArgumentException("expense.amountCents must be > 0");
        }
        validatePercentage(input.aiAssistRatio, "expense.aiAssistRatio");

        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        String userId = userContext.requireCurrentUserId();
        validateProjectAccess(database.readableDb(), allocations, userId, "expense.projectAllocations");
        String id = randomId();
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("occurred_on", input.occurredOn.trim());
            values.put("category", category);
            values.put("category_id", requireDimensionId(db, "dim_expense_category", category, "expense.category"));
            values.put("amount_cents", input.amountCents);
            if (input.aiAssistRatio != null) {
                values.put("ai_assist_ratio", input.aiAssistRatio);
            }
            values.put("note", nullSafe(input.note));
            values.put("source", "manual");
            values.put("owner_user_id", userId);
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertOrThrow("expense", null, values);
            replaceProjectLinks(db, "expense", id, allocations, userId, now);
            replaceTagLinks(db, "expense", id, tagIds, userId, now);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return id;
    }

    @Override
    public String createLearning(CreateLearningInput input) {
        requireNotBlank(input.occurredOn, "learning.occurredOn");
        requireNotBlank(input.content, "learning.content");
        requireNotBlank(input.startedAt, "learning.startedAt");
        requireNotBlank(input.endedAt, "learning.endedAt");
        String level = normalizeOrDefault(input.applicationLevel, "input");
        requireContains(LEARNING_LEVEL, level, "learning.applicationLevel");
        String startedAt = input.startedAt.trim();
        String endedAt = input.endedAt.trim();
        Instant startInstant = parseInstantStrict(startedAt, "learning.startedAt");
        Instant endInstant = parseInstantStrict(endedAt, "learning.endedAt");
        if (!endInstant.isAfter(startInstant)) {
            throw new IllegalArgumentException("learning.endedAt must be after learning.startedAt");
        }
        int durationMinutes = Math.max(1, (int) Duration.between(startInstant, endInstant).toMinutes());
        String occurredOn = startInstant.atZone(resolveZoneId()).toLocalDate().toString();
        validateScore(input.efficiencyScore, "learning.efficiencyScore");
        validatePercentage(input.aiAssistRatio, "learning.aiAssistRatio");
        requireNotNull(input.efficiencyScore, "learning.efficiencyScore");
        requireNotNull(input.aiAssistRatio, "learning.aiAssistRatio");
        validateAllocations(input.projectAllocations, "learning.projectAllocations");

        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        boolean isPublicPool = allocations.isEmpty();
        String userId = userContext.requireCurrentUserId();
        validateProjectAccess(database.readableDb(), allocations, userId, "learning.projectAllocations");
        String id = randomId();
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("occurred_on", occurredOn);
            if (!TextUtils.isEmpty(startedAt)) {
                values.put("started_at", startedAt);
            }
            if (!TextUtils.isEmpty(endedAt)) {
                values.put("ended_at", endedAt);
            }
            values.put("content", input.content.trim());
            values.put("duration_minutes", durationMinutes);
            if (input.efficiencyScore != null) {
                values.put("efficiency_score", input.efficiencyScore);
            }
            values.put("application_level", level);
            values.put("application_level_id", requireDimensionId(db, "dim_learning_level", level, "learning.applicationLevel"));
            if (input.aiAssistRatio != null) {
                values.put("ai_assist_ratio", input.aiAssistRatio);
            }
            values.put("note", nullSafe(input.note));
            values.put("source", "manual");
            values.put("is_public_pool", toInt(isPublicPool));
            values.put("owner_user_id", userId);
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertOrThrow("learning_record", null, values);
            replaceProjectLinks(db, "learning", id, allocations, userId, now);
            replaceTagLinks(db, "learning", id, tagIds, userId, now);

            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public String createTag(String name, String emoji, String tagGroup, String scope, String parentTagId, int level) {
        requireNotBlank(name, "tag.name");
        String normalizedScope = normalizeOrDefault(scope, "global");
        requireContains(TAG_SCOPE, normalizedScope, "tag.scope");
        int normalizedLevel = normalizeTagLevel(level);
        String id = randomId();
        String now = nowIso();
        String userId = userContext.requireCurrentUserId();
        String normalizedParentTagId = normalizeParentTagId(database.readableDb(), parentTagId, normalizedLevel, normalizedScope, userId, null);
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name.trim());
        values.put("emoji", nullSafe(emoji));
        values.put("tag_group", TextUtils.isEmpty(tagGroup) ? "custom" : tagGroup.trim().toLowerCase());
        values.put("scope", normalizedScope);
        values.put("parent_tag_id", normalizedParentTagId);
        values.put("level", normalizedLevel);
        values.put("sort_order", 0);
        values.put("is_system", 0);
        values.put("is_active", 1);
        values.put("owner_user_id", userId);
        values.put("created_at", now);
        values.put("updated_at", now);
        insert("tag", values);
        return id;
    }

    @Override
    public void updateProjectRecord(String projectId, CreateProjectInput input) {
        if (TextUtils.isEmpty(projectId) || input == null) {
            return;
        }
        requireNotBlank(input.name, "project.name");
        requireNotBlank(input.startedOn, "project.startedOn");
        String status = normalizeOrDefault(input.status, "active");
        requireContains(PROJECT_STATUS, status, "project.status");
        if (input.aiEnableRatio != null && (input.aiEnableRatio < 0 || input.aiEnableRatio > 100)) {
            throw new IllegalArgumentException("project.aiEnableRatio must be 0-100");
        }
        if (input.score != null && (input.score < 1 || input.score > 10)) {
            throw new IllegalArgumentException("project.score must be 1-10");
        }
        String now = nowIso();
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("name", input.name.trim());
            values.put("status", status);
            values.put("status_id", requireDimensionId(db, "dim_project_status", status, "project.status"));
            values.put("started_on", input.startedOn.trim());
            if (input.aiEnableRatio != null) {
                values.put("ai_enable_ratio", input.aiEnableRatio);
            }
            if (input.score != null) {
                values.put("score", input.score);
            }
            values.put("note", nullSafe(input.note));
            values.put("updated_at", now);
            db.update("project", values,
                    "id = ? AND is_deleted = 0 AND (owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = project.id AND pm.user_id = ?))",
                    new String[] { projectId, userId, userId });
            replaceTagLinks(db, "project", projectId, safeList(input.tagIds), userId, now);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void updateProject(String projectId, String status, int score, String note, String endedOn) {
        if (TextUtils.isEmpty(projectId))
            return;

        SQLiteDatabase db = database.writableDb();
        ContentValues values = new ContentValues();
        if (!TextUtils.isEmpty(status)) {
            String normalizedStatus = status.trim().toLowerCase();
            requireContains(PROJECT_STATUS, normalizedStatus, "project.status");
            values.put("status", normalizedStatus);
            values.put("status_id", requireDimensionId(db, "dim_project_status", normalizedStatus, "project.status"));
        }
        if (score >= 1 && score <= 10) {
            values.put("score", score);
        }
        if (note != null) {
            values.put("note", note.trim());
        }
        if (!TextUtils.isEmpty(endedOn)) {
            values.put("ended_on", endedOn.trim());
        }
        values.put("updated_at", nowIso());
        String userId = userContext.requireCurrentUserId();
        db.update(
                "project",
                values,
                "id = ? AND is_deleted = 0 AND (owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = project.id AND pm.user_id = ?))",
                new String[] { projectId, userId, userId });
    }

    @Override
    public void updateTimeLog(String timeLogId, CreateTimeLogInput input) {
        if (TextUtils.isEmpty(timeLogId) || input == null) {
            return;
        }
        requireNotBlank(input.startedAt, "timeLog.startedAt");
        requireNotBlank(input.endedAt, "timeLog.endedAt");
        String category = normalizeTimeCategory(input.category);
        validateScore(input.efficiencyScore, "timeLog.efficiencyScore");
        validateScore(input.valueScore, "timeLog.valueScore");
        validateScore(input.stateScore, "timeLog.stateScore");
        validatePercentage(input.aiAssistRatio, "timeLog.aiAssistRatio");
        validateWorkLearningRequiredFields(category, input.valueScore, input.stateScore, input.aiAssistRatio);
        validateAllocations(input.projectAllocations, "timeLog.projectAllocations");
        long durationMinutes = computeDurationMinutes(input.startedAt, input.endedAt);
        boolean hasAllocationsInput = input.projectAllocations != null;
        boolean hasTagsInput = input.tagIds != null;
        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        boolean isPublicPool = allocations.isEmpty();
        String userId = userContext.requireCurrentUserId();
        if (hasAllocationsInput) {
            validateProjectAccess(database.readableDb(), allocations, userId, "timeLog.projectAllocations");
        }
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("started_at", input.startedAt.trim());
            values.put("ended_at", input.endedAt.trim());
            values.put("duration_minutes", durationMinutes);
            values.put("category", category);
            values.put("category_id", resolveTimeCategoryId(db, category));
            if (input.efficiencyScore != null) {
                values.put("efficiency_score", input.efficiencyScore);
            } else {
                values.putNull("efficiency_score");
            }
            if (input.valueScore != null) {
                values.put("value_score", input.valueScore);
            } else {
                values.putNull("value_score");
            }
            if (input.stateScore != null) {
                values.put("state_score", input.stateScore);
            } else {
                values.putNull("state_score");
            }
            if (input.aiAssistRatio != null) {
                values.put("ai_assist_ratio", input.aiAssistRatio);
            } else {
                values.putNull("ai_assist_ratio");
            }
            values.put("note", nullSafe(input.note));
            if (hasAllocationsInput) {
                values.put("is_public_pool", toInt(isPublicPool));
            }
            values.put("updated_at", now);
            db.update("time_log", values, "id = ? AND owner_user_id = ? AND is_deleted = 0",
                    new String[] { timeLogId, userId });
            if (hasAllocationsInput) {
                replaceProjectLinks(db, "time_log", timeLogId, allocations, userId, now);
            }
            if (hasTagsInput) {
                replaceTagLinks(db, "time_log", timeLogId, tagIds, userId, now);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void updateIncome(String incomeId, CreateIncomeInput input) {
        if (TextUtils.isEmpty(incomeId) || input == null) {
            return;
        }
        requireNotBlank(input.occurredOn, "income.occurredOn");
        requireNotBlank(input.sourceName, "income.sourceName");
        String type = normalizeOrDefault(input.type, "other");
        requireContains(INCOME_TYPE, type, "income.type");
        if (input.amountCents < 0) {
            throw new IllegalArgumentException("income.amountCents must be >= 0");
        }
        validatePercentage(input.aiAssistRatio, "income.aiAssistRatio");
        validateAllocations(input.projectAllocations, "income.projectAllocations");
        boolean hasAllocationsInput = input.projectAllocations != null;
        boolean hasTagsInput = input.tagIds != null;
        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        boolean isPublicPool = allocations.isEmpty();
        String userId = userContext.requireCurrentUserId();
        if (hasAllocationsInput) {
            validateProjectAccess(database.readableDb(), allocations, userId, "income.projectAllocations");
        }
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("occurred_on", input.occurredOn.trim());
            values.put("source_name", input.sourceName.trim());
            values.put("type", type);
            values.put("type_id", requireDimensionId(db, "dim_income_type", type, "income.type"));
            values.put("amount_cents", input.amountCents);
            values.put("is_passive", toInt(input.isPassive));
            if (input.aiAssistRatio != null) {
                values.put("ai_assist_ratio", input.aiAssistRatio);
            } else {
                values.putNull("ai_assist_ratio");
            }
            values.put("note", nullSafe(input.note));
            if (hasAllocationsInput) {
                values.put("is_public_pool", toInt(isPublicPool));
            }
            values.put("updated_at", now);
            db.update("income", values, "id = ? AND owner_user_id = ? AND is_deleted = 0",
                    new String[] { incomeId, userId });
            if (hasAllocationsInput) {
                replaceProjectLinks(db, "income", incomeId, allocations, userId, now);
            }
            if (hasTagsInput) {
                replaceTagLinks(db, "income", incomeId, tagIds, userId, now);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void updateExpense(String expenseId, CreateExpenseInput input) {
        if (TextUtils.isEmpty(expenseId) || input == null) {
            return;
        }
        requireNotBlank(input.occurredOn, "expense.occurredOn");
        String category = normalizeOrDefault(input.category, "necessary");
        requireContains(EXPENSE_CATEGORY, category, "expense.category");
        if (input.amountCents <= 0) {
            throw new IllegalArgumentException("expense.amountCents must be > 0");
        }
        validatePercentage(input.aiAssistRatio, "expense.aiAssistRatio");
        boolean hasAllocationsInput = input.projectAllocations != null;
        boolean hasTagsInput = input.tagIds != null;
        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        String userId = userContext.requireCurrentUserId();
        if (hasAllocationsInput) {
            validateProjectAccess(database.readableDb(), allocations, userId, "expense.projectAllocations");
        }
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("occurred_on", input.occurredOn.trim());
            values.put("category", category);
            values.put("category_id", requireDimensionId(db, "dim_expense_category", category, "expense.category"));
            values.put("amount_cents", input.amountCents);
            if (input.aiAssistRatio != null) {
                values.put("ai_assist_ratio", input.aiAssistRatio);
            } else {
                values.putNull("ai_assist_ratio");
            }
            values.put("note", nullSafe(input.note));
            values.put("updated_at", now);
            db.update("expense", values, "id = ? AND owner_user_id = ? AND is_deleted = 0",
                    new String[] { expenseId, userId });
            if (hasAllocationsInput) {
                replaceProjectLinks(db, "expense", expenseId, allocations, userId, now);
            }
            if (hasTagsInput) {
                replaceTagLinks(db, "expense", expenseId, tagIds, userId, now);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void updateLearning(String learningId, CreateLearningInput input) {
        if (TextUtils.isEmpty(learningId) || input == null) {
            return;
        }
        requireNotBlank(input.occurredOn, "learning.occurredOn");
        requireNotBlank(input.content, "learning.content");
        requireNotBlank(input.startedAt, "learning.startedAt");
        requireNotBlank(input.endedAt, "learning.endedAt");
        String level = normalizeOrDefault(input.applicationLevel, "input");
        requireContains(LEARNING_LEVEL, level, "learning.applicationLevel");
        String startedAt = input.startedAt.trim();
        String endedAt = input.endedAt.trim();
        Instant startInstant = parseInstantStrict(startedAt, "learning.startedAt");
        Instant endInstant = parseInstantStrict(endedAt, "learning.endedAt");
        if (!endInstant.isAfter(startInstant)) {
            throw new IllegalArgumentException("learning.endedAt must be after learning.startedAt");
        }
        int durationMinutes = Math.max(1, (int) Duration.between(startInstant, endInstant).toMinutes());
        String occurredOn = startInstant.atZone(resolveZoneId()).toLocalDate().toString();
        validateScore(input.efficiencyScore, "learning.efficiencyScore");
        validatePercentage(input.aiAssistRatio, "learning.aiAssistRatio");
        requireNotNull(input.efficiencyScore, "learning.efficiencyScore");
        requireNotNull(input.aiAssistRatio, "learning.aiAssistRatio");
        validateAllocations(input.projectAllocations, "learning.projectAllocations");
        boolean hasAllocationsInput = input.projectAllocations != null;
        boolean hasTagsInput = input.tagIds != null;
        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        boolean isPublicPool = allocations.isEmpty();
        String userId = userContext.requireCurrentUserId();
        if (hasAllocationsInput) {
            validateProjectAccess(database.readableDb(), allocations, userId, "learning.projectAllocations");
        }
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("occurred_on", occurredOn);
            values.put("started_at", startedAt);
            values.put("ended_at", endedAt);
            values.put("content", input.content.trim());
            values.put("duration_minutes", durationMinutes);
            if (input.efficiencyScore != null) {
                values.put("efficiency_score", input.efficiencyScore);
            } else {
                values.putNull("efficiency_score");
            }
            values.put("application_level", level);
            values.put("application_level_id", requireDimensionId(db, "dim_learning_level", level, "learning.applicationLevel"));
            if (input.aiAssistRatio != null) {
                values.put("ai_assist_ratio", input.aiAssistRatio);
            } else {
                values.putNull("ai_assist_ratio");
            }
            values.put("note", nullSafe(input.note));
            if (hasAllocationsInput) {
                values.put("is_public_pool", toInt(isPublicPool));
            }
            values.put("updated_at", now);
            db.update("learning_record", values, "id = ? AND owner_user_id = ? AND is_deleted = 0",
                    new String[] { learningId, userId });
            if (hasAllocationsInput) {
                replaceProjectLinks(db, "learning", learningId, allocations, userId, now);
            }
            if (hasTagsInput) {
                replaceTagLinks(db, "learning", learningId, tagIds, userId, now);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void updateTag(String tagId, String name, String emoji, String tagGroup, String scope, String parentTagId, int level, boolean active) {
        if (TextUtils.isEmpty(tagId)) {
            return;
        }
        requireNotBlank(name, "tag.name");
        String normalizedScope = normalizeOrDefault(scope, "global");
        requireContains(TAG_SCOPE, normalizedScope, "tag.scope");
        String userId = userContext.requireCurrentUserId();
        int normalizedLevel = normalizeTagLevel(level);
        String normalizedParentTagId = normalizeParentTagId(database.readableDb(), parentTagId, normalizedLevel, normalizedScope, userId, tagId);
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("emoji", nullSafe(emoji));
        values.put("tag_group", TextUtils.isEmpty(tagGroup) ? "custom" : tagGroup.trim().toLowerCase());
        values.put("scope", normalizedScope);
        values.put("parent_tag_id", normalizedParentTagId);
        values.put("level", normalizedLevel);
        values.put("is_active", toInt(active));
        values.put("updated_at", nowIso());
        database.writableDb().update(
                "tag",
                values,
                "id = ? AND (owner_user_id = ? OR is_system = 0)",
                new String[] { tagId, userId });
    }

    @Override
    public void deleteRecord(String type, String recordId) {
        if (TextUtils.isEmpty(type) || TextUtils.isEmpty(recordId)) {
            return;
        }
        String table;
        switch (type.trim().toLowerCase()) {
            case "time":
                table = "time_log";
                break;
            case "income":
                table = "income";
                break;
            case "expense":
                table = "expense";
                break;
            case "learning":
                table = "learning_record";
                break;
            default:
                throw new IllegalArgumentException("Unsupported record type: " + type);
        }
        ContentValues values = new ContentValues();
        values.put("is_deleted", 1);
        values.put("updated_at", nowIso());
        database.writableDb().update(table, values, "id = ? AND owner_user_id = ? AND is_deleted = 0",
                new String[] { recordId, userContext.requireCurrentUserId() });
    }

    @Override
    public void deleteProject(String projectId) {
        if (TextUtils.isEmpty(projectId)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("is_deleted", 1);
        values.put("updated_at", nowIso());
        String userId = userContext.requireCurrentUserId();
        database.writableDb().update(
                "project",
                values,
                "id = ? AND is_deleted = 0 AND (owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = project.id AND pm.user_id = ?))",
                new String[] { projectId, userId, userId });
    }

    @Override
    public void deleteTag(String tagId) {
        if (TextUtils.isEmpty(tagId)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("is_active", 0);
        values.put("updated_at", nowIso());
        database.writableDb().update("tag", values, "id = ? AND owner_user_id = ?",
                new String[] { tagId, userContext.requireCurrentUserId() });
    }

    private void insert(String table, ContentValues values) {
        database.writableDb().insertOrThrow(table, null, values);
    }

    private void replaceProjectLinks(SQLiteDatabase db, String recordType, String entityId,
            List<ProjectAllocation> allocations, String ownerUserId, String now) {
        db.delete("record_project_link", "record_type = ? AND record_id = ?", new String[] { recordType, entityId });
        for (ProjectAllocation allocation : allocations) {
            ContentValues item = new ContentValues();
            item.put("record_type", recordType);
            item.put("record_id", entityId);
            item.put("project_id", allocation.projectId.trim());
            item.put("weight_ratio", allocation.weightRatio);
            item.put("owner_user_id", ownerUserId);
            item.put("created_at", now);
            db.insertOrThrow("record_project_link", null, item);
        }
    }

    private void replaceTagLinks(SQLiteDatabase db, String recordType, String entityId,
            List<String> tagIds, String ownerUserId, String now) {
        db.delete("record_tag_link", "record_type = ? AND record_id = ?", new String[] { recordType, entityId });
        for (String tagId : tagIds) {
            if (TextUtils.isEmpty(tagId)) {
                continue;
            }
            ContentValues item = new ContentValues();
            item.put("record_type", recordType);
            item.put("record_id", entityId);
            item.put("tag_id", tagId.trim());
            item.put("owner_user_id", ownerUserId);
            item.put("created_at", now);
            db.insertOrThrow("record_tag_link", null, item);
        }
    }

    private static int requireDimensionId(SQLiteDatabase db, String table, String code, String field) {
        try (Cursor cursor = db.rawQuery(
                "SELECT id FROM " + table + " WHERE code = ? AND is_active = 1 LIMIT 1",
                new String[] { code })) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        throw new IllegalArgumentException(field + " unsupported: " + code);
    }

    private static int resolveTimeCategoryId(SQLiteDatabase db, String categoryCode) {
        try (Cursor cursor = db.rawQuery(
                "SELECT id FROM dim_time_category WHERE code = ? LIMIT 1",
                new String[] { categoryCode })) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }

        String now = nowIso();
        ContentValues values = new ContentValues();
        values.put("code", categoryCode);
        values.put("display_name", categoryCode);
        values.put("sort_order", 1000);
        values.put("is_active", 1);
        values.put("is_system", 0);
        values.put("created_at", now);
        values.put("updated_at", now);
        db.insertWithOnConflict("dim_time_category", null, values, SQLiteDatabase.CONFLICT_IGNORE);

        return requireDimensionId(db, "dim_time_category", categoryCode, "timeLog.category");
    }

    private static long computeDurationMinutes(String startedAt, String endedAt) {
        Instant start = parseInstant(startedAt.trim());
        Instant end = parseInstant(endedAt.trim());
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            throw new IllegalArgumentException("timeLog duration must be > 0 minutes");
        }
        return minutes;
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }

    private static String nullSafe(String value) {
        return TextUtils.isEmpty(value) ? null : value.trim();
    }

    private static String trimmedOrNull(String value) {
        return TextUtils.isEmpty(value) ? null : value.trim();
    }

    private static String normalizeTimeCategory(String value) {
        requireNotBlank(value, "timeLog.category");
        return value.trim().toLowerCase();
    }

    private static int normalizeTagLevel(int level) {
        return level <= 1 ? 1 : 2;
    }

    private static String normalizeParentTagId(SQLiteDatabase db, String parentTagId, int level, String scope,
            String userId, String selfTagId) {
        if (level <= 1) {
            return null;
        }
        if (TextUtils.isEmpty(parentTagId) || TextUtils.isEmpty(parentTagId.trim())) {
            throw new IllegalArgumentException("tag.parentTagId is required when tag.level=2");
        }
        String normalizedParentId = parentTagId.trim();
        if (!TextUtils.isEmpty(selfTagId) && selfTagId.trim().equals(normalizedParentId)) {
            throw new IllegalArgumentException("tag.parentTagId cannot reference itself");
        }
        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(scope,'global'), COALESCE(level,1), owner_user_id, is_system " +
                        "FROM tag WHERE id = ? LIMIT 1",
                new String[] { normalizedParentId })) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("tag.parentTagId not found");
            }
            String parentScope = cursor.isNull(0) ? "global" : cursor.getString(0);
            int parentLevel = cursor.getInt(1);
            String parentOwnerId = cursor.isNull(2) ? null : cursor.getString(2);
            boolean parentIsSystem = cursor.getInt(3) == 1;
            if (parentLevel != 1) {
                throw new IllegalArgumentException("tag.parentTagId must point to a level-1 tag");
            }
            if (!"global".equals(parentScope) && !parentScope.equals(scope)) {
                throw new IllegalArgumentException("tag.parentTagId scope mismatch");
            }
            if (!parentIsSystem && !TextUtils.equals(parentOwnerId, userId)) {
                throw new IllegalArgumentException("tag.parentTagId is not accessible");
            }
            return normalizedParentId;
        }
    }

    private static Instant parseInstantStrict(String value, String fieldName) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " must be ISO-8601 UTC instant");
        }
    }

    private ZoneId resolveZoneId() {
        String timezone = "Asia/Shanghai";
        try (Cursor cursor = database.readableDb().rawQuery(
                "SELECT COALESCE(timezone, 'Asia/Shanghai') FROM users WHERE id = ? LIMIT 1",
                new String[] { userContext.requireCurrentUserId() })) {
            if (cursor.moveToFirst() && !cursor.isNull(0) && !TextUtils.isEmpty(cursor.getString(0))) {
                timezone = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private static String normalizeOrDefault(String value, String fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        return value.trim().toLowerCase();
    }

    private static void requireNotBlank(String value, String field) {
        if (TextUtils.isEmpty(value) || TextUtils.isEmpty(value.trim())) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void validateScore(Integer score, String field) {
        if (score != null && (score < 1 || score > 10)) {
            throw new IllegalArgumentException(field + " must be 1-10");
        }
    }

    private static void validatePercentage(Integer value, String field) {
        if (value != null && (value < 0 || value > 100)) {
            throw new IllegalArgumentException(field + " must be 0-100");
        }
    }

    private static void requireNotNull(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void validateWorkLearningRequiredFields(String category, Integer valueScore, Integer stateScore,
            Integer aiAssistRatio) {
        if (!"work".equals(category)) {
            return;
        }
        requireNotNull(valueScore, "timeLog.valueScore");
        requireNotNull(stateScore, "timeLog.stateScore");
        requireNotNull(aiAssistRatio, "timeLog.aiAssistRatio");
    }

    private static void validateAllocations(List<ProjectAllocation> allocations, String field) {
        if (allocations == null) {
            return;
        }
        for (ProjectAllocation allocation : allocations) {
            if (allocation == null) {
                throw new IllegalArgumentException(field + " contains null item");
            }
            if (TextUtils.isEmpty(allocation.projectId) || TextUtils.isEmpty(allocation.projectId.trim())) {
                throw new IllegalArgumentException(field + ".projectId is required");
            }
            if (allocation.weightRatio <= 0) {
                throw new IllegalArgumentException(field + ".weightRatio must be > 0");
            }
        }
    }

    private static void validateProjectAccess(SQLiteDatabase db, List<ProjectAllocation> allocations, String userId, String field) {
        if (allocations == null || allocations.isEmpty()) {
            return;
        }
        for (ProjectAllocation allocation : allocations) {
            if (allocation == null || TextUtils.isEmpty(allocation.projectId)) {
                continue;
            }
            String projectId = allocation.projectId.trim();
            try (Cursor cursor = db.rawQuery(
                    "SELECT 1 FROM project p WHERE p.id = ? AND p.is_deleted = 0 " +
                            "AND (p.owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = p.id AND pm.user_id = ?)) LIMIT 1",
                    new String[] { projectId, userId, userId })) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalArgumentException(field + " contains inaccessible projectId: " + projectId);
                }
            }
        }
    }

    private static void requireContains(Set<String> source, String value, String field) {
        if (!source.contains(value)) {
            throw new IllegalArgumentException(field + " invalid: " + value);
        }
    }

    private static Set<String> setOf(String... values) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, values);
        return set;
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? Collections.emptyList() : value;
    }

    private static String randomId() {
        return UUID.randomUUID().toString();
    }

    private static int toInt(boolean value) {
        return value ? 1 : 0;
    }

    private static String nowIso() {
        return Instant.now().toString();
    }
}
