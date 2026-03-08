package com.example.skyeos.data.repository;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SQLiteLifeOsWriteRepository implements LifeOsWriteRepository {
    private static final Set<String> PROJECT_STATUS = setOf("active", "paused", "done");
    private static final Set<String> TIME_CATEGORY = setOf("work", "learning", "life", "entertainment", "rest", "social");
    private static final Set<String> INCOME_TYPE = setOf("salary", "project", "investment", "system", "other");
    private static final Set<String> EXPENSE_CATEGORY = setOf("necessary", "experience", "subscription", "investment");
    private static final Set<String> LEARNING_LEVEL = setOf("input", "applied", "result");

    private final LifeOsDatabase database;

    public SQLiteLifeOsWriteRepository(LifeOsDatabase database) {
        this.database = database;
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

        String id = randomId();
        String now = nowIso();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", input.name.trim());
        values.put("status", status);
        values.put("started_on", input.startedOn.trim());
        if (input.aiEnableRatio != null) {
            values.put("ai_enable_ratio", input.aiEnableRatio);
        }
        if (input.score != null) {
            values.put("score", input.score);
        }
        values.put("note", nullSafe(input.note));
        values.put("created_at", now);
        values.put("updated_at", now);
        insert("project", values);
        return id;
    }

    @Override
    public String createTimeLog(CreateTimeLogInput input) {
        requireNotBlank(input.startedAt, "timeLog.startedAt");
        requireNotBlank(input.endedAt, "timeLog.endedAt");
        String category = normalizeOrDefault(input.category, "work");
        requireContains(TIME_CATEGORY, category, "timeLog.category");
        validateScore(input.valueScore, "timeLog.valueScore");
        validateScore(input.stateScore, "timeLog.stateScore");
        validateAllocations(input.projectAllocations, "timeLog.projectAllocations");

        long durationMinutes = computeDurationMinutes(input.startedAt, input.endedAt);
        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        boolean isPublicPool = allocations.isEmpty();

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
            if (input.valueScore != null) {
                values.put("value_score", input.valueScore);
            }
            if (input.stateScore != null) {
                values.put("state_score", input.stateScore);
            }
            values.put("note", nullSafe(input.note));
            values.put("source", "manual");
            values.put("is_public_pool", toInt(isPublicPool));
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertOrThrow("time_log", null, values);

            for (ProjectAllocation allocation : allocations) {
                ContentValues item = new ContentValues();
                item.put("time_log_id", id);
                item.put("project_id", allocation.projectId.trim());
                item.put("weight_ratio", allocation.weightRatio);
                item.put("created_at", now);
                db.insertOrThrow("time_log_project", null, item);
            }

            for (String tagId : tagIds) {
                if (TextUtils.isEmpty(tagId)) {
                    continue;
                }
                ContentValues item = new ContentValues();
                item.put("time_log_id", id);
                item.put("tag_id", tagId.trim());
                item.put("created_at", now);
                db.insertOrThrow("time_log_tag", null, item);
            }

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
        validateAllocations(input.projectAllocations, "income.projectAllocations");

        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        boolean isPublicPool = allocations.isEmpty();
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
            values.put("amount_cents", input.amountCents);
            values.put("is_passive", toInt(input.isPassive));
            values.put("note", nullSafe(input.note));
            values.put("source", "manual");
            values.put("is_public_pool", toInt(isPublicPool));
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertOrThrow("income", null, values);

            for (ProjectAllocation allocation : allocations) {
                ContentValues item = new ContentValues();
                item.put("income_id", id);
                item.put("project_id", allocation.projectId.trim());
                item.put("weight_ratio", allocation.weightRatio);
                item.put("created_at", now);
                db.insertOrThrow("income_project", null, item);
            }

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
        if (input.amountCents < 0) {
            throw new IllegalArgumentException("expense.amountCents must be >= 0");
        }

        String id = randomId();
        String now = nowIso();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("occurred_on", input.occurredOn.trim());
        values.put("category", category);
        values.put("amount_cents", input.amountCents);
        values.put("note", nullSafe(input.note));
        values.put("source", "manual");
        values.put("created_at", now);
        values.put("updated_at", now);
        insert("expense", values);
        return id;
    }

    @Override
    public String createLearning(CreateLearningInput input) {
        requireNotBlank(input.occurredOn, "learning.occurredOn");
        requireNotBlank(input.content, "learning.content");
        String level = normalizeOrDefault(input.applicationLevel, "input");
        requireContains(LEARNING_LEVEL, level, "learning.applicationLevel");
        if (input.durationMinutes < 0) {
            throw new IllegalArgumentException("learning.durationMinutes must be >= 0");
        }
        validateAllocations(input.projectAllocations, "learning.projectAllocations");

        List<ProjectAllocation> allocations = safeList(input.projectAllocations);
        List<String> tagIds = safeList(input.tagIds);
        boolean isPublicPool = allocations.isEmpty();
        String id = randomId();
        String now = nowIso();
        SQLiteDatabase db = database.writableDb();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("occurred_on", input.occurredOn.trim());
            values.put("content", input.content.trim());
            values.put("duration_minutes", input.durationMinutes);
            values.put("application_level", level);
            values.put("note", nullSafe(input.note));
            values.put("source", "manual");
            values.put("is_public_pool", toInt(isPublicPool));
            values.put("created_at", now);
            values.put("updated_at", now);
            db.insertOrThrow("learning_record", null, values);

            for (ProjectAllocation allocation : allocations) {
                ContentValues item = new ContentValues();
                item.put("learning_id", id);
                item.put("project_id", allocation.projectId.trim());
                item.put("weight_ratio", allocation.weightRatio);
                item.put("created_at", now);
                db.insertOrThrow("learning_project", null, item);
            }

            for (String tagId : tagIds) {
                if (TextUtils.isEmpty(tagId)) {
                    continue;
                }
                ContentValues item = new ContentValues();
                item.put("learning_id", id);
                item.put("tag_id", tagId.trim());
                item.put("created_at", now);
                db.insertOrThrow("learning_tag", null, item);
            }

            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    private void insert(String table, ContentValues values) {
        database.writableDb().insertOrThrow(table, null, values);
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
