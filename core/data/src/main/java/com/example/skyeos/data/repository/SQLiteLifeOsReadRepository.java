package com.example.skyeos.data.repository;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.domain.model.ProjectOption;
import com.example.skyeos.domain.model.ProjectOverview;
import com.example.skyeos.domain.model.ProjectDetail;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.TagItem;
import com.example.skyeos.domain.model.WindowOverview;
import com.example.skyeos.domain.repository.LifeOsReadRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

public final class SQLiteLifeOsReadRepository implements LifeOsReadRepository {
    private static final Set<String> WINDOW_TYPES = Set.of("day", "week", "month", "year");
    private final LifeOsDatabase database;
    private final CurrentUserContext userContext;

    @Inject
    public SQLiteLifeOsReadRepository(LifeOsDatabase database, CurrentUserContext userContext) {
        this.database = database;
        this.userContext = userContext;
    }

    @Override
    public WindowOverview getOverview(String anchorDate, String windowType) {
        String date = normalizeDate(anchorDate);
        String window = normalizeWindow(windowType);
        DateRange range = DateRange.from(date, window);
        String timezone = queryTimezone();
        String rangeStartUtc = toUtcStart(range.startDate, timezone);
        String rangeEndUtcExclusive = toUtcEndExclusive(range.endDate, timezone);
        String userId = userContext.requireCurrentUserId();

        long totalIncome = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId, range.startDate, range.endDate);
        long actualExpense = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM expense WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId, range.startDate, range.endDate);
        long structuralExpense = structuralExpenseForWindow(userId, range.startDate, range.endDate, false);
        long totalExpense = actualExpense + structuralExpense;
        long totalWorkMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
                userId, rangeStartUtc, rangeEndUtcExclusive);
        long totalTimeMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category != 'learning' AND started_at >= ? AND started_at < ?",
                userId, rangeStartUtc, rangeEndUtcExclusive);
        long publicTimeMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND is_public_pool = 1 AND category != 'learning' AND started_at >= ? AND started_at < ?",
                userId, rangeStartUtc, rangeEndUtcExclusive);
        long publicIncome = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND is_public_pool = 1 AND occurred_on >= ? AND occurred_on <= ?",
                userId, range.startDate, range.endDate);
        long totalLearningMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM learning_record WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId, range.startDate, range.endDate);
        long publicLearningMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM learning_record WHERE owner_user_id = ? AND is_deleted = 0 AND is_public_pool = 1 AND occurred_on >= ? AND occurred_on <= ?",
                userId, range.startDate, range.endDate);

        long netIncome = totalIncome - totalExpense;
        double publicTimeRatio = ratio(publicTimeMinutes, totalTimeMinutes);
        double publicIncomeRatio = ratio(publicIncome, totalIncome);
        double publicLearningRatio = ratio(publicLearningMinutes, totalLearningMinutes);

        return new WindowOverview(
                window,
                date,
                range.startDate,
                range.endDate,
                totalIncome,
                actualExpense,
                structuralExpense,
                totalExpense,
                netIncome,
                totalWorkMinutes,
                totalTimeMinutes,
                totalLearningMinutes,
                publicTimeRatio,
                publicIncomeRatio,
                publicLearningRatio);
    }

    @Override
    public List<RecentRecordItem> getRecentRecords(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        String sql = "SELECT record_id, type, occurred_at, title, detail FROM (" +
                "SELECT id AS record_id, 'time' AS type, started_at AS occurred_at, category AS title, COALESCE(note, '') AS detail FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 "
                +
                "UNION ALL " +
                "SELECT id AS record_id, 'income' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, source_name AS title, amount_cents || ' cents' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM income WHERE owner_user_id = ? AND is_deleted = 0 "
                +
                "UNION ALL " +
                "SELECT id AS record_id, 'expense' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, category AS title, amount_cents || ' cents' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM expense WHERE owner_user_id = ? AND is_deleted = 0 "
                +
                "UNION ALL " +
                "SELECT id AS record_id, 'learning' AS type, COALESCE(started_at, occurred_on || 'T00:00:00Z') AS occurred_at, content AS title, duration_minutes || ' min' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM learning_record WHERE owner_user_id = ? AND is_deleted = 0"
                +
                ") ORDER BY occurred_at DESC LIMIT " + safeLimit;
        List<RecentRecordItem> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, new String[] { userId, userId, userId, userId })) {
            while (cursor.moveToNext()) {
                result.add(new RecentRecordItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4)));
            }
        }
        return result;
    }

    @Override
    public List<RecentRecordItem> getRecordsForDate(String anchorDate, int limit) {
        String date = normalizeDate(anchorDate);
        String timezone = queryTimezone();
        String dateStartUtc = toUtcStart(date, timezone);
        String dateEndUtcExclusive = toUtcEndExclusive(date, timezone);
        int safeLimit = Math.max(1, Math.min(limit, 300));
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        String sql = "SELECT record_id, type, occurred_at, title, detail FROM (" +
                "SELECT id AS record_id, 'time' AS type, started_at AS occurred_at, category AS title, COALESCE(note, '') AS detail FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND started_at >= ? AND started_at < ? " +
                "UNION ALL " +
                "SELECT id AS record_id, 'income' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, source_name AS title, amount_cents || ' cents' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on = ? " +
                "UNION ALL " +
                "SELECT id AS record_id, 'expense' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, category AS title, amount_cents || ' cents' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM expense WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on = ? " +
                "UNION ALL " +
                "SELECT id AS record_id, 'learning' AS type, COALESCE(started_at, occurred_on || 'T00:00:00Z') AS occurred_at, content AS title, duration_minutes || ' min' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM learning_record WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on = ? " +
                ") ORDER BY occurred_at DESC LIMIT " + safeLimit;
        List<RecentRecordItem> result = new ArrayList<>();
        String[] args = new String[] { userId, dateStartUtc, dateEndUtcExclusive, userId, date, userId, date, userId, date };
        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                result.add(new RecentRecordItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4)));
            }
        }
        return result;
    }

    @Override
    public List<ProjectOption> getProjectOptions(boolean includeDone) {
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        String sql = includeDone
                ? "SELECT p.id, p.name, p.status FROM project p WHERE p.is_deleted = 0 AND (p.owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = p.id AND pm.user_id = ?)) ORDER BY p.updated_at DESC"
                : "SELECT p.id, p.name, p.status FROM project p WHERE p.is_deleted = 0 AND p.status != 'done' AND (p.owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = p.id AND pm.user_id = ?)) ORDER BY p.updated_at DESC";
        List<ProjectOption> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, new String[] { userId, userId })) {
            while (cursor.moveToNext()) {
                result.add(new ProjectOption(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2)));
            }
        }
        return result;
    }

    @Override
    public List<ProjectOverview> getProjects(String status) {
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        List<ProjectOverview> result = new ArrayList<>();
        // Query projects along with aggregated time (minutes) and income (cents) from
        // relation tables
        String sql = "SELECT p.id, p.name, p.status, p.score, " +
                "  COALESCE((SELECT SUM(t.duration_minutes) FROM time_log t JOIN record_project_link rpl ON rpl.record_type = 'time_log' AND rpl.record_id = t.id WHERE rpl.project_id = p.id AND t.is_deleted = 0), 0) as total_time, "
                +
                "  COALESCE((SELECT SUM(i.amount_cents) FROM income i JOIN record_project_link rpl ON rpl.record_type = 'income' AND rpl.record_id = i.id WHERE rpl.project_id = p.id AND i.is_deleted = 0), 0) as total_income "
                +
                "FROM project p " +
                "WHERE p.is_deleted = 0 " +
                "AND (p.owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = p.id AND pm.user_id = ?)) " +
                (status != null ? "AND p.status = ? " : "") +
                "ORDER BY p.updated_at DESC";

        String[] args = status != null
                ? new String[] { userId, userId, status }
                : new String[] { userId, userId };
        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                result.add(new ProjectOverview(
                        cursor.getString(0), // id
                        cursor.getString(1), // name
                        cursor.getString(2), // status
                        cursor.getInt(3), // score
                        cursor.getLong(4), // time
                        cursor.getLong(5) // income
                ));
            }
        }
        return result;
    }

    @Override
    public ProjectDetail getProjectDetail(String projectId) {
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();

        // 1. Get base project info
        String sql = "SELECT id, name, status, started_on, ended_on, score, note " +
                "FROM project WHERE id = ? AND is_deleted = 0 " +
                "AND (owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = project.id AND pm.user_id = ?))";

        String name = "", status = "", startedOn = "", endedOn = "", note = "";
        int score = 0;

        try (Cursor cursor = db.rawQuery(sql, new String[] { projectId, userId, userId })) {
            if (cursor.moveToFirst()) {
                name = cursor.getString(1);
                status = cursor.getString(2);
                startedOn = cursor.getString(3);
                endedOn = cursor.getString(4);
                score = cursor.getInt(5);
                note = cursor.getString(6);
            } else {
                return null; // Project not found
            }
        }

        // 2. Get metrics
        long totalTime = scalarLong(
                "SELECT COALESCE(SUM(t.duration_minutes), 0) FROM time_log t JOIN record_project_link rpl ON rpl.record_type = 'time_log' AND rpl.record_id = t.id WHERE rpl.project_id = ? AND t.is_deleted = 0",
                projectId);
        long totalIncome = scalarLong(
                "SELECT COALESCE(SUM(i.amount_cents), 0) FROM income i JOIN record_project_link rpl ON rpl.record_type = 'income' AND rpl.record_id = i.id WHERE rpl.project_id = ? AND i.is_deleted = 0",
                projectId);
        long totalExpense = scalarLong(
                "SELECT COALESCE(SUM(e.amount_cents), 0) FROM expense e JOIN record_project_link rpl ON rpl.record_type = 'expense' AND rpl.record_id = e.id WHERE rpl.project_id = ? AND e.is_deleted = 0",
                projectId);
        String analysisStartDate = normalizeProjectBoundary(
                startedOn,
                firstProjectActivityDate(projectId),
                LocalDate.now().toString());
        String analysisEndDate = normalizeProjectBoundary(
                endedOn,
                lastProjectActivityDate(projectId),
                LocalDate.now().toString());
        if (LocalDate.parse(analysisEndDate).isBefore(LocalDate.parse(analysisStartDate))) {
            analysisEndDate = analysisStartDate;
        }
        String timezone = queryTimezone();
        String analysisStartUtc = toUtcStart(analysisStartDate, timezone);
        String analysisEndUtcExclusive = toUtcEndExclusive(analysisEndDate, timezone);
        long structuralCostWindow = structuralExpenseForWindow(userId, analysisStartDate, analysisEndDate, false);
        long globalWorkMinutesInWindow = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
                userId, analysisStartUtc, analysisEndUtcExclusive);
        long globalWorkMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work'",
                userId);
        long globalIncomeCents = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0",
                userId);
        long idealHourlyRateCents = scalarLong(
                "SELECT COALESCE(ideal_hourly_rate_cents, 0) FROM users WHERE id = ? LIMIT 1",
                userId);
        long actualHourlyRateCents = globalWorkMinutes > 0 ? (globalIncomeCents * 60 / globalWorkMinutes) : 0L;
        int lastYear = LocalDate.now().minusYears(1).getYear();
        String lastYearStart = String.format(Locale.US, "%04d-01-01", lastYear);
        String lastYearEnd = String.format(Locale.US, "%04d-12-31", lastYear);
        String lastYearStartUtc = toUtcStart(lastYearStart, timezone);
        String lastYearEndUtcExclusive = toUtcEndExclusive(lastYearEnd, timezone);
        long lastYearIncomeCents = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId, lastYearStart, lastYearEnd);
        long lastYearWorkMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
                userId, lastYearStartUtc, lastYearEndUtcExclusive);
        long lastYearHourlyRateCents = lastYearWorkMinutes > 0 ? (lastYearIncomeCents * 60 / lastYearWorkMinutes) : 0L;

        long benchmarkHourlyRateCents = lastYearHourlyRateCents > 0
                ? lastYearHourlyRateCents
                : (idealHourlyRateCents > 0 ? idealHourlyRateCents : actualHourlyRateCents);
        long timeCostCents = benchmarkHourlyRateCents > 0 ? (benchmarkHourlyRateCents * totalTime / 60) : 0L;
        long allocatedStructuralCostCents = 0L;
        if (structuralCostWindow > 0 && globalWorkMinutesInWindow > 0 && totalTime > 0) {
            allocatedStructuralCostCents = structuralCostWindow * totalTime / globalWorkMinutesInWindow;
        }
        long operatingCostCents = totalExpense + timeCostCents;
        long operatingProfitCents = totalIncome - operatingCostCents;
        long operatingBreakEvenIncomeCents = operatingCostCents;
        long fullyLoadedCostCents = operatingCostCents + allocatedStructuralCostCents;
        long fullyLoadedProfitCents = totalIncome - fullyLoadedCostCents;
        long fullyLoadedBreakEvenIncomeCents = fullyLoadedCostCents;
        long totalCostCents = fullyLoadedCostCents;
        long profitCents = fullyLoadedProfitCents;
        long breakEvenIncomeCents = fullyLoadedBreakEvenIncomeCents;

        double hourlyRate = 0;
        if (totalTime > 0) {
            double hours = totalTime / 60.0;
            hourlyRate = (totalIncome / 100.0) / hours;
        }

        double operatingRoi = roi(totalIncome, operatingCostCents);
        double fullyLoadedRoi = roi(totalIncome, fullyLoadedCostCents);
        double roi = fullyLoadedRoi;

        // 3. Get recent records associated with this project
        String recentsSql = "SELECT record_id, type, occurred_at, title, detail FROM (" +
                "SELECT t.id AS record_id, 'time' as type, t.started_at as occurred_at, t.category as title, t.note as detail FROM time_log t JOIN record_project_link rpl ON rpl.record_type = 'time_log' AND rpl.record_id = t.id WHERE rpl.project_id = ? AND t.is_deleted = 0 "
                +
                "UNION ALL " +
                "SELECT i.id AS record_id, 'income' as type, i.occurred_on || 'T00:00:00Z', i.source_name, i.amount_cents || ' cents' FROM income i JOIN record_project_link rpl ON rpl.record_type = 'income' AND rpl.record_id = i.id WHERE rpl.project_id = ? AND i.is_deleted = 0 "
                +
                "UNION ALL " +
                "SELECT e.id AS record_id, 'expense' as type, e.occurred_on || 'T00:00:00Z', e.category, e.amount_cents || ' cents' || CASE WHEN e.note IS NULL OR e.note = '' THEN '' ELSE ' | ' || e.note END FROM expense e JOIN record_project_link rpl ON rpl.record_type = 'expense' AND rpl.record_id = e.id WHERE rpl.project_id = ? AND e.is_deleted = 0 "
                +
                ") ORDER BY occurred_at DESC LIMIT 50";

        List<RecentRecordItem> recentRecords = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(recentsSql, new String[] { projectId, projectId, projectId })) {
            while (cursor.moveToNext()) {
                recentRecords.add(new RecentRecordItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4)));
            }
        }

        return new ProjectDetail(
                projectId, name, status, startedOn, endedOn, score, note,
                analysisStartDate, analysisEndDate,
                totalTime, totalIncome, totalExpense, totalExpense, timeCostCents, totalCostCents, profitCents,
                breakEvenIncomeCents, allocatedStructuralCostCents, operatingCostCents, operatingProfitCents,
                operatingBreakEvenIncomeCents, fullyLoadedCostCents, fullyLoadedProfitCents,
                fullyLoadedBreakEvenIncomeCents, benchmarkHourlyRateCents, lastYearHourlyRateCents,
                idealHourlyRateCents, hourlyRate, roi, operatingRoi, fullyLoadedRoi, recentRecords);
    }

    @Override
    public List<TagItem> getTags(String scope, boolean activeOnly) {
        SQLiteDatabase db = database.readableDb();
        String userId = userContext.requireCurrentUserId();
        List<TagItem> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, COALESCE(emoji,''), tag_group, COALESCE(scope,'global'), parent_tag_id, COALESCE(level, 1), is_system, is_active FROM tag WHERE 1=1");
        List<String> args = new ArrayList<>();
        sql.append(" AND (owner_user_id = ? OR is_system = 1)");
        args.add(userId);
        if (!TextUtils.isEmpty(scope) && !"all".equalsIgnoreCase(scope.trim())) {
            sql.append(" AND (scope = ? OR scope = 'global')");
            args.add(scope.trim().toLowerCase(Locale.US));
        }
        if (activeOnly) {
            sql.append(" AND is_active = 1");
        }
        sql.append(" ORDER BY COALESCE(level,1) ASC, COALESCE(parent_tag_id,''), sort_order ASC, is_system DESC, updated_at DESC, name COLLATE NOCASE ASC");
        try (Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                result.add(new TagItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.isNull(5) ? null : cursor.getString(5),
                        cursor.getInt(6),
                        cursor.getInt(7) == 1,
                        cursor.getInt(8) == 1));
            }
        }
        return result;
    }

    private long scalarLong(String sql, String... args) {
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return 0L;
            }
            return cursor.getLong(0);
        }
    }

    private String scalarString(String sql, String... args) {
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return cursor.getString(0);
        }
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / (double) denominator;
    }

    private static double roi(long incomeCents, long costCents) {
        if (costCents <= 0) {
            return 0.0;
        }
        return ((double) (incomeCents - costCents) / costCents) * 100.0;
    }

    private String firstProjectActivityDate(String projectId) {
        String timezone = queryTimezone();
        LocalDate earliest = null;
        earliest = earlierDate(earliest, parseProjectTimeLogDate(scalarString(
                "SELECT MIN(t.started_at) FROM time_log t JOIN record_project_link rpl ON rpl.record_type = 'time_log' AND rpl.record_id = t.id WHERE rpl.project_id = ? AND t.is_deleted = 0",
                projectId), timezone));
        earliest = earlierDate(earliest, parseProjectDayDate(scalarString(
                "SELECT MIN(i.occurred_on) FROM income i JOIN record_project_link rpl ON rpl.record_type = 'income' AND rpl.record_id = i.id WHERE rpl.project_id = ? AND i.is_deleted = 0",
                projectId)));
        earliest = earlierDate(earliest, parseProjectDayDate(scalarString(
                "SELECT MIN(e.occurred_on) FROM expense e JOIN record_project_link rpl ON rpl.record_type = 'expense' AND rpl.record_id = e.id WHERE rpl.project_id = ? AND e.is_deleted = 0",
                projectId)));
        return earliest == null ? null : earliest.toString();
    }

    private String lastProjectActivityDate(String projectId) {
        String timezone = queryTimezone();
        LocalDate latest = null;
        latest = laterDate(latest, parseProjectTimeLogDate(scalarString(
                "SELECT MAX(t.started_at) FROM time_log t JOIN record_project_link rpl ON rpl.record_type = 'time_log' AND rpl.record_id = t.id WHERE rpl.project_id = ? AND t.is_deleted = 0",
                projectId), timezone));
        latest = laterDate(latest, parseProjectDayDate(scalarString(
                "SELECT MAX(i.occurred_on) FROM income i JOIN record_project_link rpl ON rpl.record_type = 'income' AND rpl.record_id = i.id WHERE rpl.project_id = ? AND i.is_deleted = 0",
                projectId)));
        latest = laterDate(latest, parseProjectDayDate(scalarString(
                "SELECT MAX(e.occurred_on) FROM expense e JOIN record_project_link rpl ON rpl.record_type = 'expense' AND rpl.record_id = e.id WHERE rpl.project_id = ? AND e.is_deleted = 0",
                projectId)));
        return latest == null ? null : latest.toString();
    }

    private static LocalDate parseProjectDayDate(String date) {
        if (TextUtils.isEmpty(date)) {
            return null;
        }
        return LocalDate.parse(date.trim());
    }

    private static LocalDate parseProjectTimeLogDate(String startedAt, String timezone) {
        if (TextUtils.isEmpty(startedAt)) {
            return null;
        }
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(TextUtils.isEmpty(timezone) ? "Asia/Shanghai" : timezone);
        } catch (RuntimeException ignore) {
            zoneId = ZoneId.of("Asia/Shanghai");
        }
        try {
            return Instant.parse(startedAt.trim()).atZone(zoneId).toLocalDate();
        } catch (RuntimeException ignore) {
            return OffsetDateTime.parse(startedAt.trim()).atZoneSameInstant(zoneId).toLocalDate();
        }
    }

    private static LocalDate earlierDate(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }

    private static LocalDate laterDate(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }

    private static String normalizeProjectBoundary(String preferred, String fallback, String hardFallback) {
        if (!TextUtils.isEmpty(preferred)) {
            return LocalDate.parse(preferred.trim()).toString();
        }
        if (!TextUtils.isEmpty(fallback)) {
            return LocalDate.parse(fallback.trim()).toString();
        }
        return LocalDate.parse(hardFallback.trim()).toString();
    }

    private long structuralExpenseForWindow(String userId, String startDate, String endDate, boolean necessaryOnly) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (end.isBefore(start)) {
            return 0L;
        }
        SQLiteDatabase db = database.readableDb();
        long total = 0L;
        java.time.YearMonth cursor = java.time.YearMonth.from(start);
        java.time.YearMonth target = java.time.YearMonth.from(end);
        while (!cursor.isAfter(target)) {
            LocalDate monthStart = cursor.atDay(1);
            LocalDate monthEnd = cursor.atEndOfMonth();
            LocalDate overlapStart = start.isAfter(monthStart) ? start : monthStart;
            LocalDate overlapEnd = end.isBefore(monthEnd) ? end : monthEnd;
            if (!overlapEnd.isBefore(overlapStart)) {
                long overlapDays = java.time.temporal.ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
                int monthDays = cursor.lengthOfMonth();
                long monthTotal = baselineMonthlyCost(db, userId, cursor.toString())
                        + recurringMonthlyCost(db, userId, cursor.toString(), necessaryOnly)
                        + (necessaryOnly ? 0L : capexMonthlyCost(db, userId, cursor.toString()));
                total += monthTotal * overlapDays / monthDays;
            }
            cursor = cursor.plusMonths(1);
        }
        return total;
    }

    private long baselineMonthlyCost(SQLiteDatabase db, String userId, String month) {
        return scalarLong(
                "SELECT COALESCE(basic_living_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                userId, month);
    }

    private long recurringMonthlyCost(SQLiteDatabase db, String userId, String month, boolean necessaryOnly) {
        if (necessaryOnly) {
            return scalarLong(
                    "SELECT COALESCE(SUM(monthly_amount_cents), 0) FROM expense_recurring_rule WHERE owner_user_id = ? AND is_active = 1 AND is_necessary = 1 AND start_month <= ? AND (end_month IS NULL OR end_month = '' OR end_month >= ?)",
                    userId, month, month);
        }
        return scalarLong(
                "SELECT COALESCE(SUM(monthly_amount_cents), 0) FROM expense_recurring_rule WHERE owner_user_id = ? AND is_active = 1 AND start_month <= ? AND (end_month IS NULL OR end_month = '' OR end_month >= ?)",
                userId, month, month);
    }

    private long capexMonthlyCost(SQLiteDatabase db, String userId, String month) {
        return scalarLong(
                "SELECT COALESCE(SUM(monthly_amortized_cents), 0) FROM expense_capex WHERE owner_user_id = ? AND is_active = 1 AND amortization_start_month <= ? AND amortization_end_month >= ?",
                userId, month, month);
    }

    private static String normalizeDate(String value) {
        if (TextUtils.isEmpty(value)) {
            return LocalDate.now().toString();
        }
        return LocalDate.parse(value.trim()).toString();
    }

    private String queryTimezone() {
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery("SELECT timezone FROM users WHERE id = ? LIMIT 1",
                new String[] { userContext.requireCurrentUserId() })) {
            if (cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (!TextUtils.isEmpty(value)) {
                    return value;
                }
            }
        }
        return "Asia/Shanghai";
    }

    private static String toUtcStart(String date, String timezone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(TextUtils.isEmpty(timezone) ? "Asia/Shanghai" : timezone);
        } catch (RuntimeException ignore) {
            zoneId = ZoneId.of("Asia/Shanghai");
        }
        LocalDate localDate = LocalDate.parse(date);
        return ZonedDateTime.of(localDate, LocalTime.MIN, zoneId).toInstant().toString();
    }

    private static String toUtcEndExclusive(String date, String timezone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(TextUtils.isEmpty(timezone) ? "Asia/Shanghai" : timezone);
        } catch (RuntimeException ignore) {
            zoneId = ZoneId.of("Asia/Shanghai");
        }
        LocalDate localDate = LocalDate.parse(date).plusDays(1);
        return ZonedDateTime.of(localDate, LocalTime.MIN, zoneId).toInstant().toString();
    }

    private static String normalizeWindow(String value) {
        String normalized = TextUtils.isEmpty(value) ? "day" : value.trim().toLowerCase(Locale.US);
        if (!WINDOW_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid windowType: " + normalized);
        }
        return normalized;
    }

    private static final class DateRange {
        final String startDate;
        final String endDate;

        private DateRange(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        static DateRange from(String anchorDate, String windowType) {
            LocalDate anchor = LocalDate.parse(anchorDate);
            LocalDate start;
            LocalDate end;
            switch (windowType) {
                case "day":
                    start = anchor;
                    end = anchor;
                    break;
                case "week":
                    start = anchor.minusDays(6);
                    end = anchor;
                    break;
                case "month":
                    start = anchor.withDayOfMonth(1);
                    end = anchor.withDayOfMonth(anchor.lengthOfMonth());
                    break;
                case "year":
                    start = anchor.withDayOfYear(1);
                    end = anchor.withDayOfYear(anchor.lengthOfYear());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported windowType: " + windowType);
            }
            return new DateRange(start.toString(), end.toString());
        }
    }
}
