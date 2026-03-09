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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SQLiteLifeOsReadRepository implements LifeOsReadRepository {
    private static final Set<String> WINDOW_TYPES = Set.of("day", "week", "month", "year");
    private final LifeOsDatabase database;
    private final CurrentUserContext userContext;

    public SQLiteLifeOsReadRepository(LifeOsDatabase database, CurrentUserContext userContext) {
        this.database = database;
        this.userContext = userContext;
    }

    @Override
    public WindowOverview getOverview(String anchorDate, String windowType) {
        String date = normalizeDate(anchorDate);
        String window = normalizeWindow(windowType);
        DateRange range = DateRange.from(date, window);
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
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND date(started_at) >= ? AND date(started_at) <= ?",
                userId, range.startDate, range.endDate);
        long totalTimeMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND date(started_at) >= ? AND date(started_at) <= ?",
                userId, range.startDate, range.endDate);
        long publicTimeMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND is_public_pool = 1 AND date(started_at) >= ? AND date(started_at) <= ?",
                userId, range.startDate, range.endDate);
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
        int safeLimit = Math.max(1, Math.min(limit, 300));
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        String sql = "SELECT record_id, type, occurred_at, title, detail FROM (" +
                "SELECT id AS record_id, 'time' AS type, started_at AS occurred_at, category AS title, COALESCE(note, '') AS detail FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND date(started_at) = ? " +
                "UNION ALL " +
                "SELECT id AS record_id, 'income' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, source_name AS title, amount_cents || ' cents' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on = ? " +
                "UNION ALL " +
                "SELECT id AS record_id, 'expense' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, category AS title, amount_cents || ' cents' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM expense WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on = ? " +
                "UNION ALL " +
                "SELECT id AS record_id, 'learning' AS type, COALESCE(started_at, occurred_on || 'T00:00:00Z') AS occurred_at, content AS title, duration_minutes || ' min' || CASE WHEN note IS NULL OR note = '' THEN '' ELSE ' | ' || note END AS detail FROM learning_record WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on = ? " +
                ") ORDER BY occurred_at DESC LIMIT " + safeLimit;
        List<RecentRecordItem> result = new ArrayList<>();
        String[] args = new String[] { userId, date, userId, date, userId, date, userId, date };
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
                "  COALESCE((SELECT SUM(t.duration_minutes) FROM time_log t JOIN time_log_project tp ON t.id = tp.time_log_id WHERE tp.project_id = p.id AND t.is_deleted = 0), 0) as total_time, "
                +
                "  COALESCE((SELECT SUM(i.amount_cents) FROM income i JOIN income_project ip ON i.id = ip.income_id WHERE ip.project_id = p.id AND i.is_deleted = 0), 0) as total_income "
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
                "SELECT COALESCE(SUM(t.duration_minutes), 0) FROM time_log t JOIN time_log_project tp ON t.id = tp.time_log_id WHERE tp.project_id = ? AND t.is_deleted = 0",
                projectId);
        long totalIncome = scalarLong(
                "SELECT COALESCE(SUM(i.amount_cents), 0) FROM income i JOIN income_project ip ON i.id = ip.income_id WHERE ip.project_id = ? AND i.is_deleted = 0",
                projectId);
        long totalExpense = scalarLong(
                "SELECT COALESCE(SUM(e.amount_cents), 0) FROM expense e JOIN expense_project ep ON e.id = ep.expense_id WHERE ep.project_id = ? AND e.is_deleted = 0",
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
        long structuralCostWindow = structuralExpenseForWindow(userId, analysisStartDate, analysisEndDate, false);
        long globalWorkMinutesInWindow = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND date(started_at) >= ? AND date(started_at) <= ?",
                userId, analysisStartDate, analysisEndDate);
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
        long lastYearIncomeCents = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId, lastYearStart, lastYearEnd);
        long lastYearWorkMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND date(started_at) >= ? AND date(started_at) <= ?",
                userId, lastYearStart, lastYearEnd);
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
                "SELECT t.id AS record_id, 'time' as type, t.started_at as occurred_at, t.category as title, t.note as detail FROM time_log t JOIN time_log_project tp ON t.id = tp.time_log_id WHERE tp.project_id = ? AND t.is_deleted = 0 "
                +
                "UNION ALL " +
                "SELECT i.id AS record_id, 'income' as type, i.occurred_on || 'T00:00:00Z', i.source_name, i.amount_cents || ' cents' FROM income i JOIN income_project ip ON i.id = ip.income_id WHERE ip.project_id = ? AND i.is_deleted = 0 "
                +
                "UNION ALL " +
                "SELECT e.id AS record_id, 'expense' as type, e.occurred_on || 'T00:00:00Z', e.category, e.amount_cents || ' cents' || CASE WHEN e.note IS NULL OR e.note = '' THEN '' ELSE ' | ' || e.note END FROM expense e JOIN expense_project ep ON e.id = ep.expense_id WHERE ep.project_id = ? AND e.is_deleted = 0 "
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
                "SELECT id, name, COALESCE(emoji,''), tag_group, COALESCE(scope,'global'), is_system, is_active FROM tag WHERE 1=1");
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
        sql.append(" ORDER BY sort_order ASC, is_system DESC, updated_at DESC, name COLLATE NOCASE ASC");
        try (Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                result.add(new TagItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getInt(5) == 1,
                        cursor.getInt(6) == 1));
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
        return scalarString(
                "SELECT MIN(activity_date) FROM (" +
                        "SELECT date(t.started_at) AS activity_date FROM time_log t JOIN time_log_project tp ON t.id = tp.time_log_id WHERE tp.project_id = ? AND t.is_deleted = 0 " +
                        "UNION ALL " +
                        "SELECT i.occurred_on AS activity_date FROM income i JOIN income_project ip ON i.id = ip.income_id WHERE ip.project_id = ? AND i.is_deleted = 0 " +
                        "UNION ALL " +
                        "SELECT e.occurred_on AS activity_date FROM expense e JOIN expense_project ep ON e.id = ep.expense_id WHERE ep.project_id = ? AND e.is_deleted = 0" +
                        ")",
                projectId, projectId, projectId);
    }

    private String lastProjectActivityDate(String projectId) {
        return scalarString(
                "SELECT MAX(activity_date) FROM (" +
                        "SELECT date(t.started_at) AS activity_date FROM time_log t JOIN time_log_project tp ON t.id = tp.time_log_id WHERE tp.project_id = ? AND t.is_deleted = 0 " +
                        "UNION ALL " +
                        "SELECT i.occurred_on AS activity_date FROM income i JOIN income_project ip ON i.id = ip.income_id WHERE ip.project_id = ? AND i.is_deleted = 0 " +
                        "UNION ALL " +
                        "SELECT e.occurred_on AS activity_date FROM expense e JOIN expense_project ep ON e.id = ep.expense_id WHERE ep.project_id = ? AND e.is_deleted = 0" +
                        ")",
                projectId, projectId, projectId);
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
                "SELECT COALESCE(basic_living_cents, 0) + COALESCE(fixed_subscription_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
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
