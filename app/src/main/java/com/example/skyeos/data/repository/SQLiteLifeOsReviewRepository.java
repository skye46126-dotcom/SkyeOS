package com.example.skyeos.data.repository;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.domain.model.ProjectProgressItem;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.ReviewReport;
import com.example.skyeos.domain.repository.LifeOsReviewRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class SQLiteLifeOsReviewRepository implements LifeOsReviewRepository {

    private final LifeOsDatabase database;
    private final CurrentUserContext userContext;

    public SQLiteLifeOsReviewRepository(LifeOsDatabase database, CurrentUserContext userContext) {
        this.database = database;
        this.userContext = userContext;
    }

    @Override
    public ReviewReport getDailyReview(String date) {
        LocalDate d = LocalDate.parse(date);
        LocalDate prev = d.minusDays(1);
        return buildReport(
                "Daily Review (" + date + ")",
                d + " 00:00:00",
                d + " 23:59:59",
                prev + " 00:00:00",
                prev + " 23:59:59");
    }

    @Override
    public ReviewReport getWeeklyReview(String weekStart, String weekEnd) {
        LocalDate ws = LocalDate.parse(weekStart);
        LocalDate we = LocalDate.parse(weekEnd);
        LocalDate prevStart = ws.minusDays(7);
        LocalDate prevEnd = we.minusDays(7);
        return buildReport(
                "Weekly Report (" + weekStart + " to " + weekEnd + ")",
                ws + " 00:00:00",
                we + " 23:59:59",
                prevStart + " 00:00:00",
                prevEnd + " 23:59:59");
    }

    @Override
    public ReviewReport getMonthlyReview(String monthStart, String monthEnd) {
        LocalDate ms = LocalDate.parse(monthStart);
        LocalDate me = LocalDate.parse(monthEnd);
        LocalDate prevMonthAnchor = ms.minusMonths(1);
        LocalDate prevStart = prevMonthAnchor.withDayOfMonth(1);
        LocalDate prevEnd = prevMonthAnchor.withDayOfMonth(prevMonthAnchor.lengthOfMonth());
        return buildReport(
                "Monthly Report (" + monthStart + " to " + monthEnd + ")",
                ms + " 00:00:00",
                me + " 23:59:59",
                prevStart + " 00:00:00",
                prevEnd + " 23:59:59");
    }

    @Override
    public ReviewReport getYearlyReview(String yearStart, String yearEnd) {
        LocalDate ys = LocalDate.parse(yearStart);
        LocalDate ye = LocalDate.parse(yearEnd);
        LocalDate prevYearAnchor = ys.minusYears(1);
        LocalDate prevStart = prevYearAnchor.withDayOfYear(1);
        LocalDate prevEnd = prevYearAnchor.withDayOfYear(prevYearAnchor.lengthOfYear());
        return buildReport(
                "Yearly Report (" + yearStart + " to " + yearEnd + ")",
                ys + " 00:00:00",
                ye + " 23:59:59",
                prevStart + " 00:00:00",
                prevEnd + " 23:59:59");
    }

    @Override
    public ReviewReport getRangeReview(String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevStart = start.minusDays(days);
        LocalDate prevEnd = end.minusDays(days);
        return buildReport(
                "Custom Range (" + start + " to " + end + ")",
                start + " 00:00:00",
                end + " 23:59:59",
                prevStart + " 00:00:00",
                prevEnd + " 23:59:59");
    }

    @Override
    public List<RecentRecordItem> getTagDetailRecords(String scope, String tagName, String startDate, String endDate,
            int limit) {
        if (TextUtils.isEmpty(scope) || TextUtils.isEmpty(tagName)) {
            return new ArrayList<>();
        }
        String timezone = queryTimezone();
        String startAtUtc = toUtcStart(startDate, timezone);
        String endAtUtcExclusive = toUtcEndExclusive(endDate, timezone);
        String userId = userContext.requireCurrentUserId();
        int rowLimit = Math.max(1, Math.min(limit, 200));
        SQLiteDatabase db = database.readableDb();
        List<RecentRecordItem> rows = new ArrayList<>();
        String normalizedScope = scope.trim().toLowerCase();
        if ("time".equals(normalizedScope)) {
            String sql = "SELECT tl.started_at, tl.category, tl.duration_minutes, COALESCE(tl.note,'') " +
                    "FROM time_log_tag tlt " +
                    "JOIN time_log tl ON tl.id = tlt.time_log_id " +
                    "JOIN tag tg ON tg.id = tlt.tag_id " +
                    "WHERE tl.is_deleted = 0 AND tl.owner_user_id = ? AND tg.name = ? AND tl.started_at >= ? AND tl.started_at < ? " +
                    "ORDER BY tl.started_at DESC LIMIT " + rowLimit;
            try (Cursor c = db.rawQuery(sql, new String[] { userId, tagName, startAtUtc, endAtUtcExclusive })) {
                while (c.moveToNext()) {
                    String occurredAt = c.isNull(0) ? "" : c.getString(0);
                    String category = c.isNull(1) ? "-" : c.getString(1);
                    long mins = c.isNull(2) ? 0L : c.getLong(2);
                    String note = c.isNull(3) ? "" : c.getString(3);
                    rows.add(new RecentRecordItem("time", occurredAt, category, mins + " min"
                            + (TextUtils.isEmpty(note) ? "" : " | " + note)));
                }
            }
            return rows;
        }
        if ("expense".equals(normalizedScope)) {
            String sql = "SELECT e.occurred_on, e.category, e.amount_cents, COALESCE(e.note,'') " +
                    "FROM expense_tag et " +
                    "JOIN expense e ON e.id = et.expense_id " +
                    "JOIN tag tg ON tg.id = et.tag_id " +
                    "WHERE e.is_deleted = 0 AND e.owner_user_id = ? AND tg.name = ? AND e.occurred_on >= ? AND e.occurred_on <= ? " +
                    "ORDER BY e.occurred_on DESC, e.created_at DESC LIMIT " + rowLimit;
            try (Cursor c = db.rawQuery(sql, new String[] { userId, tagName, startDate, endDate })) {
                while (c.moveToNext()) {
                    String occurredAt = c.isNull(0) ? "" : c.getString(0) + "T00:00:00Z";
                    String category = c.isNull(1) ? "-" : c.getString(1);
                    long cents = c.isNull(2) ? 0L : c.getLong(2);
                    String note = c.isNull(3) ? "" : c.getString(3);
                    rows.add(new RecentRecordItem("expense", occurredAt, category, cents + " cents"
                            + (TextUtils.isEmpty(note) ? "" : " | " + note)));
                }
            }
            return rows;
        }
        return rows;
    }

    private ReviewReport buildReport(String periodName, String startTimeString, String endTimeString,
            String prevStartTimeString, String prevEndTimeString) {
        SQLiteDatabase db = database.readableDb();
        String userId = userContext.requireCurrentUserId();
        String startDate = startTimeString.substring(0, 10);
        String endDate = endTimeString.substring(0, 10);
        String prevStartDate = prevStartTimeString.substring(0, 10);
        String prevEndDate = prevEndTimeString.substring(0, 10);
        String timezone = queryTimezone();
        String startAtUtc = toUtcStart(startDate, timezone);
        String endAtUtcExclusive = toUtcEndExclusive(endDate, timezone);
        String prevStartAtUtc = toUtcStart(prevStartDate, timezone);
        String prevEndAtUtcExclusive = toUtcEndExclusive(prevEndDate, timezone);

        long totalTime = 0;
        long totalWorkMinutes = 0;
        long totalIncome = 0;
        long totalExpense = 0;
        long prevIncome = 0;
        long prevExpense = 0;
        long prevWorkMinutes = 0;
        List<ReviewReport.TimeCategoryAllocation> allocations = new ArrayList<>();
        List<ProjectProgressItem> topProjects = new ArrayList<>();
        List<ProjectProgressItem> sinkholeProjects = new ArrayList<>();
        List<RecentRecordItem> keyEvents = new ArrayList<>();
        List<RecentRecordItem> incomeHistory = new ArrayList<>();
        List<RecentRecordItem> historyRecords = new ArrayList<>();
        List<ReviewReport.TagMetric> timeTagMetrics = new ArrayList<>();
        List<ReviewReport.TagMetric> expenseTagMetrics = new ArrayList<>();

        // 1. Time Allocation & Total Time
        String timeSql = "SELECT category, SUM(duration_minutes) as mins FROM time_log " +
                "WHERE owner_user_id = ? AND started_at >= ? AND started_at < ? AND is_deleted = 0 " +
                "GROUP BY category ORDER BY mins DESC";
        try (Cursor cursor = db.rawQuery(timeSql, new String[] { userId, startAtUtc, endAtUtcExclusive })) {
            while (cursor.moveToNext()) {
                String cat = cursor.getString(0);
                long mins = cursor.getLong(1);
                allocations.add(new ReviewReport.TimeCategoryAllocation(cat, mins, 0)); // % calculated later
                totalTime += mins;
            }
        }
        if (totalTime > 0) {
            for (int i = 0; i < allocations.size(); i++) {
                ReviewReport.TimeCategoryAllocation a = allocations.get(i);
                allocations.set(i, new ReviewReport.TimeCategoryAllocation(
                        a.categoryName, a.minutes, (double) a.minutes / totalTime * 100.0));
            }
        }

        // 2. Financials
        totalIncome = scalarLong(db,
                "SELECT SUM(amount_cents) FROM income WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? AND is_deleted = 0",
                userId, startDate, endDate);
        totalExpense = scalarLong(db,
                "SELECT SUM(amount_cents) FROM expense WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? AND is_deleted = 0",
                userId, startDate, endDate);
        totalExpense += structuralExpenseForWindow(userId, startDate, endDate, false);
        totalWorkMinutes = scalarLong(db,
                "SELECT SUM(duration_minutes) FROM time_log WHERE owner_user_id = ? AND started_at >= ? AND started_at < ? AND is_deleted = 0 AND category = 'work'",
                userId, startAtUtc, endAtUtcExclusive);
        prevIncome = scalarLong(db,
                "SELECT SUM(amount_cents) FROM income WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? AND is_deleted = 0",
                userId, prevStartDate, prevEndDate);
        prevExpense = scalarLong(db,
                "SELECT SUM(amount_cents) FROM expense WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? AND is_deleted = 0",
                userId, prevStartDate, prevEndDate);
        prevExpense += structuralExpenseForWindow(userId, prevStartDate, prevEndDate, false);
        prevWorkMinutes = scalarLong(db,
                "SELECT SUM(duration_minutes) FROM time_log WHERE owner_user_id = ? AND started_at >= ? AND started_at < ? AND is_deleted = 0 AND category = 'work'",
                userId, prevStartAtUtc, prevEndAtUtcExclusive);

        // 3. Project ROI (Top vs Sinkhole)
        long projectBenchmarkHourlyRateCents = benchmarkHourlyRateCents(db, userId);
        long structuralExpenseWindow = structuralExpenseForWindow(userId, startDate, endDate, false);
        String projSql = "SELECT * FROM (" +
                "SELECT p.id, p.name, " +
                "  COALESCE((SELECT SUM(t.duration_minutes) FROM time_log t JOIN time_log_project tp ON t.id = tp.time_log_id WHERE tp.project_id = p.id AND t.started_at >= ? AND t.started_at < ? AND t.is_deleted = 0), 0) as pt, "
                +
                "  COALESCE((SELECT SUM(i.amount_cents) FROM income i JOIN income_project ip ON i.id = ip.income_id WHERE ip.project_id = p.id AND i.occurred_on >= ? AND i.occurred_on <= ? AND i.is_deleted = 0), 0) as pi, "
                +
                "  COALESCE((SELECT SUM(e.amount_cents) FROM expense e JOIN expense_project ep ON e.id = ep.expense_id WHERE ep.project_id = p.id AND e.occurred_on >= ? AND e.occurred_on <= ? AND e.is_deleted = 0), 0) as pe "
                +
                "FROM project p WHERE p.is_deleted = 0 AND (p.owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = p.id AND pm.user_id = ?))" +
                ") ranked WHERE ranked.pt > 0 OR ranked.pi > 0 ORDER BY ranked.pi DESC, ranked.pt DESC";

        try (Cursor cursor = db.rawQuery(projSql,
                new String[] { startAtUtc, endAtUtcExclusive, startDate, endDate, startDate, endDate, userId, userId })) {
            while (cursor.moveToNext()) {
                String pId = cursor.getString(0);
                String pName = cursor.getString(1);
                long pt = cursor.getLong(2);
                long pi = cursor.getLong(3);
                long pe = cursor.getLong(4);

                double hourly = 0;
                if (pt > 0) {
                    hourly = ((double) pi / 100.0) / ((double) pt / 60.0);
                }
                long timeCostCents = projectBenchmarkHourlyRateCents > 0 ? (projectBenchmarkHourlyRateCents * pt / 60) : 0L;
                long allocatedStructuralCostCents = 0L;
                if (structuralExpenseWindow > 0 && totalWorkMinutes > 0 && pt > 0) {
                    allocatedStructuralCostCents = structuralExpenseWindow * pt / totalWorkMinutes;
                }
                long operatingCostCents = pe + timeCostCents;
                long fullyLoadedCostCents = operatingCostCents + allocatedStructuralCostCents;
                double operatingRoi = roi(pi, operatingCostCents);
                double fullyLoadedRoi = roi(pi, fullyLoadedCostCents);

                String statusMsg;
                if (operatingRoi > 0 || (pi > 0 && pt == 0)) {
                    statusMsg = "positive";
                    topProjects.add(new ProjectProgressItem(
                            pId, pName, pt, pi, pe, allocatedStructuralCostCents,
                            operatingCostCents, fullyLoadedCostCents, hourly, operatingRoi, fullyLoadedRoi, statusMsg));
                } else if (pt > 120 && pi == 0) {
                    statusMsg = "warning";
                    sinkholeProjects.add(new ProjectProgressItem(
                            pId, pName, pt, pi, pe, allocatedStructuralCostCents,
                            operatingCostCents, fullyLoadedCostCents, hourly, operatingRoi, fullyLoadedRoi, statusMsg));
                } else {
                    statusMsg = "neutral";
                    if (pt > 0 || pi > 0 || pe > 0) {
                        topProjects.add(new ProjectProgressItem(
                                pId, pName, pt, pi, pe, allocatedStructuralCostCents,
                                operatingCostCents, fullyLoadedCostCents, hourly, operatingRoi, fullyLoadedRoi, statusMsg));
                    }
                }
            }
        }

        // 4. Key Events (e.g. big expenses, long time logs)
        keyEvents = fetchKeyEvents(db, startDate, endDate, startAtUtc, endAtUtcExclusive);
        incomeHistory = fetchIncomeHistory(db, userId, startDate, endDate);
        historyRecords = fetchHistoryRecords(db, userId, startDate, endDate, startAtUtc, endAtUtcExclusive);
        timeTagMetrics = fetchTimeTagMetrics(db, userId, startAtUtc, endAtUtcExclusive);
        expenseTagMetrics = fetchExpenseTagMetrics(db, userId, startDate, endDate);

        long idealHourlyRateCents = scalarLong(db,
                "SELECT COALESCE(ideal_hourly_rate_cents, 0) FROM users WHERE id = ? LIMIT 1",
                userId);
        Long actualHourlyRateCents = totalWorkMinutes > 0 ? (totalIncome * 60 / totalWorkMinutes) : null;
        Long timeDebtCents = actualHourlyRateCents == null ? null : idealHourlyRateCents - actualHourlyRateCents;
        long passiveIncome = scalarLong(db,
                "SELECT SUM(amount_cents) FROM income WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? AND is_deleted = 0 AND is_passive = 1",
                userId, startDate, endDate);
        long necessaryExpense = scalarLong(db,
                "SELECT SUM(amount_cents) FROM expense WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? AND is_deleted = 0 AND category = 'necessary'",
                userId, startDate, endDate);
        necessaryExpense += structuralExpenseForWindow(userId, startDate, endDate, true);
        Double passiveCoverRatio = necessaryExpense > 0 ? (double) passiveIncome / (double) necessaryExpense : null;

        Double freedomDelta = null;
        Double currentFreedomPercentage = null;
        double aiAssistMinutes = scalarDouble(db,
                "SELECT COALESCE(SUM((duration_minutes * COALESCE(ai_assist_ratio, 0)) / 100.0), 0) " +
                        "FROM time_log WHERE owner_user_id = ? AND started_at >= ? AND started_at < ? AND is_deleted = 0",
                userId, startAtUtc, endAtUtcExclusive);
        Double aiAssistRate = totalTime > 0 ? (aiAssistMinutes / (double) totalTime) : null;
        double weightedWorkEffNumerator = scalarDouble(db,
                "SELECT COALESCE(SUM(duration_minutes * efficiency_score), 0) " +
                        "FROM time_log WHERE owner_user_id = ? AND started_at >= ? AND started_at < ? " +
                        "AND is_deleted = 0 AND category = 'work' AND efficiency_score IS NOT NULL",
                userId, startAtUtc, endAtUtcExclusive);
        long weightedWorkEffDenominator = scalarLong(db,
                "SELECT COALESCE(SUM(duration_minutes), 0) " +
                        "FROM time_log WHERE owner_user_id = ? AND started_at >= ? AND started_at < ? " +
                        "AND is_deleted = 0 AND category = 'work' AND efficiency_score IS NOT NULL",
                userId, startAtUtc, endAtUtcExclusive);
        Double workEfficiencyAvg = weightedWorkEffDenominator > 0 ? (weightedWorkEffNumerator / weightedWorkEffDenominator) : null;

        double weightedLearningEffNumerator = scalarDouble(db,
                "SELECT COALESCE(SUM(duration_minutes * efficiency_score), 0) " +
                        "FROM learning_record WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? " +
                        "AND is_deleted = 0 AND efficiency_score IS NOT NULL",
                userId, startDate, endDate);
        long weightedLearningEffDenominator = scalarLong(db,
                "SELECT COALESCE(SUM(duration_minutes), 0) " +
                        "FROM learning_record WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? " +
                        "AND is_deleted = 0 AND efficiency_score IS NOT NULL",
                userId, startDate, endDate);
        Double learningEfficiencyAvg = weightedLearningEffDenominator > 0
                ? (weightedLearningEffNumerator / weightedLearningEffDenominator)
                : null;
        String summary;
        if (totalTime <= 0 && totalIncome <= 0 && totalExpense <= 0) {
            summary = "本期暂无有效记录。";
        } else {
            summary = String.format(
                    "本期投入 %d 小时，收入 ¥%.2f，支出 ¥%.2f。",
                    totalTime / 60, totalIncome / 100.0, totalExpense / 100.0);
            if (aiAssistRate != null) {
                summary = summary + String.format(" AI辅助率 %.1f%%。", aiAssistRate * 100.0);
            }
            if (workEfficiencyAvg != null) {
                summary = summary + String.format(" 工作效率均分 %.2f/10。", workEfficiencyAvg);
            }
            if (learningEfficiencyAvg != null) {
                summary = summary + String.format(" 学习效率均分 %.2f/10。", learningEfficiencyAvg);
            }
        }
        Double incomeChangeRatio = ratioChange(totalIncome, prevIncome);
        Double expenseChangeRatio = ratioChange(totalExpense, prevExpense);
        Double workChangeRatio = ratioChange(totalWorkMinutes, prevWorkMinutes);

        return new ReviewReport(periodName, summary, totalTime, totalWorkMinutes, totalIncome, totalExpense,
                prevIncome, prevExpense, prevWorkMinutes, incomeChangeRatio, expenseChangeRatio, workChangeRatio,
                actualHourlyRateCents, idealHourlyRateCents, timeDebtCents, passiveCoverRatio, freedomDelta,
                currentFreedomPercentage, aiAssistRate, workEfficiencyAvg, learningEfficiencyAvg,
                allocations, topProjects, sinkholeProjects, keyEvents, incomeHistory,
                historyRecords, timeTagMetrics, expenseTagMetrics);
    }

    private static Double ratioChange(long current, long previous) {
        if (previous <= 0) {
            return null;
        }
        return ((double) current - (double) previous) / (double) previous;
    }

    private List<RecentRecordItem> fetchKeyEvents(SQLiteDatabase db, String startDate, String endDate, String startAtUtc,
            String endAtUtcExclusive) {
        String userId = userContext.requireCurrentUserId();
        List<RecentRecordItem> evts = new ArrayList<>();
        // Fetch top 2 biggest expenses
        String expSql = "SELECT amount_cents, category, occurred_on, note FROM expense WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? AND is_deleted = 0 ORDER BY amount_cents DESC LIMIT 2";
        try (Cursor c = db.rawQuery(expSql, new String[] { userId, startDate, endDate })) {
            while (c.moveToNext()) {
                evts.add(new RecentRecordItem("expense", c.getString(2), "Big Expense: ¥" + (c.getLong(0) / 100.0),
                        c.getString(1) + " " + c.getString(3)));
            }
        }
        // Fetch top 2 longest time logs
        String tSql = "SELECT duration_minutes, category, started_at, note FROM time_log WHERE owner_user_id = ? AND started_at >= ? AND started_at < ? AND is_deleted = 0 ORDER BY duration_minutes DESC LIMIT 2";
        try (Cursor c = db.rawQuery(tSql, new String[] { userId, startAtUtc, endAtUtcExclusive })) {
            while (c.moveToNext()) {
                evts.add(new RecentRecordItem("time_log", c.getString(2), "Deep Work: " + c.getLong(0) + "m",
                        c.getString(1) + " " + c.getString(3)));
            }
        }
        return evts;
    }

    private List<RecentRecordItem> fetchIncomeHistory(SQLiteDatabase db, String userId, String startDate, String endDate) {
        List<RecentRecordItem> rows = new ArrayList<>();
        String sql = "SELECT occurred_on, source_name, amount_cents, type, note " +
                "FROM income WHERE owner_user_id = ? AND occurred_on >= ? AND occurred_on <= ? AND is_deleted = 0 " +
                "ORDER BY occurred_on DESC, created_at DESC LIMIT 120";
        try (Cursor c = db.rawQuery(sql, new String[] { userId, startDate, endDate })) {
            while (c.moveToNext()) {
                String occurredOn = c.isNull(0) ? "" : c.getString(0);
                String sourceName = c.isNull(1) ? "-" : c.getString(1);
                long amountCents = c.isNull(2) ? 0L : c.getLong(2);
                String type = c.isNull(3) ? "other" : c.getString(3);
                String note = c.isNull(4) ? "" : c.getString(4);
                String detail = amountCents + " cents | " + type + (TextUtils.isEmpty(note) ? "" : " | " + note);
                rows.add(new RecentRecordItem(
                        "income",
                        occurredOn + "T00:00:00Z",
                        sourceName,
                        detail));
            }
        }
        return rows;
    }

    private List<RecentRecordItem> fetchHistoryRecords(SQLiteDatabase db, String userId, String startDate, String endDate,
            String startAtUtc, String endAtUtcExclusive) {
        List<RecentRecordItem> rows = new ArrayList<>();
        String sql = "SELECT type, occurred_at, title, detail FROM (" +
                "SELECT 'time' AS type, t.started_at AS occurred_at, t.category AS title, COALESCE(t.note, '') AS detail FROM time_log t " +
                "WHERE t.owner_user_id = ? AND t.started_at >= ? AND t.started_at < ? AND t.is_deleted = 0 " +
                "UNION ALL " +
                "SELECT 'income' AS type, i.occurred_on || 'T00:00:00Z' AS occurred_at, i.source_name AS title, i.amount_cents || ' cents' || CASE WHEN i.note IS NULL OR i.note = '' THEN '' ELSE ' | ' || i.note END AS detail FROM income i " +
                "WHERE i.owner_user_id = ? AND i.occurred_on >= ? AND i.occurred_on <= ? AND i.is_deleted = 0 " +
                "UNION ALL " +
                "SELECT 'expense' AS type, e.occurred_on || 'T00:00:00Z' AS occurred_at, e.category AS title, e.amount_cents || ' cents' || CASE WHEN e.note IS NULL OR e.note = '' THEN '' ELSE ' | ' || e.note END AS detail FROM expense e " +
                "WHERE e.owner_user_id = ? AND e.occurred_on >= ? AND e.occurred_on <= ? AND e.is_deleted = 0 " +
                "UNION ALL " +
                "SELECT 'learning' AS type, COALESCE(l.started_at, l.occurred_on || 'T00:00:00Z') AS occurred_at, l.content AS title, l.duration_minutes || ' min' || CASE WHEN l.note IS NULL OR l.note = '' THEN '' ELSE ' | ' || l.note END AS detail FROM learning_record l " +
                "WHERE l.owner_user_id = ? AND l.occurred_on >= ? AND l.occurred_on <= ? AND l.is_deleted = 0" +
                ") ORDER BY occurred_at DESC LIMIT 200";
        String[] args = new String[] { userId, startAtUtc, endAtUtcExclusive, userId, startDate, endDate, userId, startDate, endDate, userId, startDate, endDate };
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) {
                rows.add(new RecentRecordItem(
                        c.getString(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3)));
            }
        }
        return rows;
    }

    private List<ReviewReport.TagMetric> fetchTimeTagMetrics(SQLiteDatabase db, String userId, String startAtUtc,
            String endAtUtcExclusive) {
        List<ReviewReport.TagMetric> result = new ArrayList<>();
        String sql = "SELECT tg.name, COALESCE(tg.emoji,''), SUM(tl.duration_minutes) AS total_minutes " +
                "FROM time_log_tag tlt " +
                "JOIN time_log tl ON tl.id = tlt.time_log_id " +
                "JOIN tag tg ON tg.id = tlt.tag_id " +
                "WHERE tl.owner_user_id = ? AND tl.started_at >= ? AND tl.started_at < ? AND tl.is_deleted = 0 AND tg.is_active = 1 " +
                "GROUP BY tg.id, tg.name, tg.emoji " +
                "ORDER BY total_minutes DESC LIMIT 8";
        long sum = 0L;
        List<Object[]> rows = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, new String[] { userId, startAtUtc, endAtUtcExclusive })) {
            while (c.moveToNext()) {
                String name = c.isNull(0) ? "-" : c.getString(0);
                String emoji = c.isNull(1) ? "" : c.getString(1);
                long value = c.isNull(2) ? 0L : c.getLong(2);
                rows.add(new Object[] { name, emoji, value });
                sum += value;
            }
        }
        for (Object[] row : rows) {
            String name = (String) row[0];
            String emoji = (String) row[1];
            long value = (Long) row[2];
            double pct = sum <= 0 ? 0.0 : ((double) value * 100.0 / (double) sum);
            result.add(new ReviewReport.TagMetric(name, emoji, value, pct));
        }
        return result;
    }

    private List<ReviewReport.TagMetric> fetchExpenseTagMetrics(SQLiteDatabase db, String userId, String startDate, String endDate) {
        List<ReviewReport.TagMetric> result = new ArrayList<>();
        String sql = "SELECT tg.name, COALESCE(tg.emoji,''), SUM(e.amount_cents) AS total_cents " +
                "FROM expense_tag et " +
                "JOIN expense e ON e.id = et.expense_id " +
                "JOIN tag tg ON tg.id = et.tag_id " +
                "WHERE e.owner_user_id = ? AND e.occurred_on >= ? AND e.occurred_on <= ? AND e.is_deleted = 0 AND tg.is_active = 1 " +
                "GROUP BY tg.id, tg.name, tg.emoji " +
                "ORDER BY total_cents DESC LIMIT 8";
        long sum = 0L;
        List<Object[]> rows = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, new String[] { userId, startDate, endDate })) {
            while (c.moveToNext()) {
                String name = c.isNull(0) ? "-" : c.getString(0);
                String emoji = c.isNull(1) ? "" : c.getString(1);
                long value = c.isNull(2) ? 0L : c.getLong(2);
                rows.add(new Object[] { name, emoji, value });
                sum += value;
            }
        }
        for (Object[] row : rows) {
            String name = (String) row[0];
            String emoji = (String) row[1];
            long value = (Long) row[2];
            double pct = sum <= 0 ? 0.0 : ((double) value * 100.0 / (double) sum);
            result.add(new ReviewReport.TagMetric(name, emoji, value, pct));
        }
        return result;
    }

    private long structuralExpenseForWindow(String userId, String startDate, String endDate, boolean necessaryOnly) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (end.isBefore(start)) {
            return 0L;
        }
        SQLiteDatabase db = database.readableDb();
        long total = 0L;
        YearMonth cursor = YearMonth.from(start);
        YearMonth target = YearMonth.from(end);
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
        return scalarLong(db,
                "SELECT COALESCE(basic_living_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                userId, month);
    }

    private long recurringMonthlyCost(SQLiteDatabase db, String userId, String month, boolean necessaryOnly) {
        if (necessaryOnly) {
            return scalarLong(db,
                    "SELECT COALESCE(SUM(monthly_amount_cents), 0) FROM expense_recurring_rule WHERE owner_user_id = ? AND is_active = 1 AND is_necessary = 1 AND start_month <= ? AND (end_month IS NULL OR end_month = '' OR end_month >= ?)",
                    userId, month, month);
        }
        return scalarLong(db,
                "SELECT COALESCE(SUM(monthly_amount_cents), 0) FROM expense_recurring_rule WHERE owner_user_id = ? AND is_active = 1 AND start_month <= ? AND (end_month IS NULL OR end_month = '' OR end_month >= ?)",
                userId, month, month);
    }

    private long capexMonthlyCost(SQLiteDatabase db, String userId, String month) {
        return scalarLong(db,
                "SELECT COALESCE(SUM(monthly_amortized_cents), 0) FROM expense_capex WHERE owner_user_id = ? AND is_active = 1 AND amortization_start_month <= ? AND amortization_end_month >= ?",
                userId, month, month);
    }

    private long scalarLong(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return 0L;
            }
            return cursor.getLong(0);
        }
    }

    private double scalarDouble(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return 0.0;
            }
            return cursor.getDouble(0);
        }
    }

    private long benchmarkHourlyRateCents(SQLiteDatabase db, String userId) {
        long idealHourlyRateCents = scalarLong(db,
                "SELECT COALESCE(ideal_hourly_rate_cents, 0) FROM users WHERE id = ? LIMIT 1",
                userId);
        String timezone = queryTimezone();
        int lastYear = LocalDate.now().minusYears(1).getYear();
        String lastYearStart = String.format(java.util.Locale.US, "%04d-01-01", lastYear);
        String lastYearEnd = String.format(java.util.Locale.US, "%04d-12-31", lastYear);
        long lastYearIncomeCents = scalarLong(db,
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId, lastYearStart, lastYearEnd);
        String lastYearStartAtUtc = toUtcStart(lastYearStart, timezone);
        String lastYearEndAtUtcExclusive = toUtcEndExclusive(lastYearEnd, timezone);
        long lastYearWorkMinutes = scalarLong(db,
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
                userId, lastYearStartAtUtc, lastYearEndAtUtcExclusive);
        if (lastYearWorkMinutes > 0) {
            return lastYearIncomeCents * 60 / lastYearWorkMinutes;
        }
        if (idealHourlyRateCents > 0) {
            return idealHourlyRateCents;
        }
        long totalIncome = scalarLong(db,
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0",
                userId);
        long totalWorkMinutes = scalarLong(db,
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work'",
                userId);
        return totalWorkMinutes > 0 ? (totalIncome * 60 / totalWorkMinutes) : 0L;
    }

    private static double roi(long incomeCents, long costCents) {
        if (costCents <= 0L) {
            return 0.0;
        }
        return ((double) (incomeCents - costCents) / costCents) * 100.0;
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
}
