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
    public List<RecentRecordItem> getTagDetailRecords(String scope, String tagName, String startDate, String endDate,
            int limit) {
        if (TextUtils.isEmpty(scope) || TextUtils.isEmpty(tagName)) {
            return new ArrayList<>();
        }
        String start = startDate + " 00:00:00";
        String end = endDate + " 23:59:59";
        String userId = userContext.requireCurrentUserId();
        int rowLimit = Math.max(1, Math.min(limit, 200));
        SQLiteDatabase db = database.readableDb();
        List<RecentRecordItem> rows = new ArrayList<>();
        String normalizedScope = scope.trim().toLowerCase();
        if ("time".equals(normalizedScope)) {
            String sql = "SELECT tl.updated_at, tl.category, tl.duration_minutes, COALESCE(tl.note,'') " +
                    "FROM time_log_tag tlt " +
                    "JOIN time_log tl ON tl.id = tlt.time_log_id " +
                    "JOIN tag tg ON tg.id = tlt.tag_id " +
                    "WHERE tl.is_deleted = 0 AND tl.owner_user_id = ? AND tg.name = ? AND tl.updated_at >= ? AND tl.updated_at <= ? " +
                    "ORDER BY tl.updated_at DESC LIMIT " + rowLimit;
            try (Cursor c = db.rawQuery(sql, new String[] { userId, tagName, start, end })) {
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
                    "WHERE e.is_deleted = 0 AND e.owner_user_id = ? AND tg.name = ? AND e.updated_at >= ? AND e.updated_at <= ? " +
                    "ORDER BY e.occurred_on DESC, e.created_at DESC LIMIT " + rowLimit;
            try (Cursor c = db.rawQuery(sql, new String[] { userId, tagName, start, end })) {
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
                "WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0 " +
                "GROUP BY category ORDER BY mins DESC";
        try (Cursor cursor = db.rawQuery(timeSql, new String[] { userId, startTimeString, endTimeString })) {
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
                "SELECT SUM(amount_cents) FROM income WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0",
                userId, startTimeString, endTimeString);
        totalExpense = scalarLong(db,
                "SELECT SUM(amount_cents) FROM expense WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0",
                userId, startTimeString, endTimeString);
        totalWorkMinutes = scalarLong(db,
                "SELECT SUM(duration_minutes) FROM time_log WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0 AND category = 'work'",
                userId, startTimeString, endTimeString);
        prevIncome = scalarLong(db,
                "SELECT SUM(amount_cents) FROM income WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0",
                userId, prevStartTimeString, prevEndTimeString);
        prevExpense = scalarLong(db,
                "SELECT SUM(amount_cents) FROM expense WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0",
                userId, prevStartTimeString, prevEndTimeString);
        prevWorkMinutes = scalarLong(db,
                "SELECT SUM(duration_minutes) FROM time_log WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0 AND category = 'work'",
                userId, prevStartTimeString, prevEndTimeString);

        // 3. Project ROI (Top vs Sinkhole)
        String projSql = "SELECT p.id, p.name, " +
                "  COALESCE((SELECT SUM(duration_minutes) FROM time_log t JOIN fact_project_allocation f ON t.id = f.record_id WHERE f.project_id = p.id AND f.record_type = 'time_log' AND t.updated_at >= ? AND t.updated_at <= ? AND t.is_deleted = 0), 0) as pt, "
                +
                "  COALESCE((SELECT SUM(amount_cents) FROM income i JOIN fact_project_allocation f ON i.id = f.record_id WHERE f.project_id = p.id AND f.record_type = 'income' AND i.updated_at >= ? AND i.updated_at <= ? AND i.is_deleted = 0), 0) as pi "
                +
                "FROM project p WHERE p.is_deleted = 0 AND (p.owner_user_id = ? OR EXISTS (SELECT 1 FROM project_member pm WHERE pm.project_id = p.id AND pm.user_id = ?)) HAVING pt > 0 OR pi > 0 ORDER BY pi DESC, pt DESC";

        try (Cursor cursor = db.rawQuery(projSql,
                new String[] { startTimeString, endTimeString, startTimeString, endTimeString, userId, userId })) {
            while (cursor.moveToNext()) {
                String pId = cursor.getString(0);
                String pName = cursor.getString(1);
                long pt = cursor.getLong(2);
                long pi = cursor.getLong(3);

                double hourly = 0;
                if (pt > 0) {
                    hourly = ((double) pi / 100.0) / ((double) pt / 60.0);
                }

                String statusMsg;
                if (hourly > 50.0 || (pi > 0 && pt == 0)) {
                    statusMsg = "positive";
                    topProjects.add(new ProjectProgressItem(pId, pName, pt, pi, hourly, statusMsg));
                } else if (pt > 120 && pi == 0) {
                    statusMsg = "warning";
                    sinkholeProjects.add(new ProjectProgressItem(pId, pName, pt, pi, hourly, statusMsg));
                } else {
                    statusMsg = "neutral";
                    // maybe add to a generic list if needed, skipping for brevity
                    if (hourly > 0) {
                        topProjects.add(new ProjectProgressItem(pId, pName, pt, pi, hourly, statusMsg));
                    }
                }
            }
        }

        // 4. Key Events (e.g. big expenses, long time logs)
        keyEvents = fetchKeyEvents(db, startTimeString, endTimeString);
        incomeHistory = fetchIncomeHistory(db, userId, startTimeString, endTimeString);
        historyRecords = fetchHistoryRecords(db, userId, startTimeString, endTimeString);
        timeTagMetrics = fetchTimeTagMetrics(db, userId, startTimeString, endTimeString);
        expenseTagMetrics = fetchExpenseTagMetrics(db, userId, startTimeString, endTimeString);

        long idealHourlyRateCents = scalarLong(db,
                "SELECT COALESCE(ideal_hourly_rate_cents, 0) FROM users WHERE id = ? LIMIT 1",
                userId);
        Long actualHourlyRateCents = totalWorkMinutes > 0 ? (totalIncome * 60 / totalWorkMinutes) : null;
        Long timeDebtCents = actualHourlyRateCents == null ? null : idealHourlyRateCents - actualHourlyRateCents;
        long passiveIncome = scalarLong(db,
                "SELECT SUM(amount_cents) FROM income WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0 AND is_passive = 1",
                userId, startTimeString, endTimeString);
        long necessaryExpense = scalarLong(db,
                "SELECT SUM(amount_cents) FROM expense WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0 AND category = 'necessary'",
                userId, startTimeString, endTimeString);
        Double passiveCoverRatio = necessaryExpense > 0 ? (double) passiveIncome / (double) necessaryExpense : null;

        Double freedomDelta = null;
        Double currentFreedomPercentage = null;
        String summary;
        if (totalTime <= 0 && totalIncome <= 0 && totalExpense <= 0) {
            summary = "本期暂无有效记录。";
        } else {
            summary = String.format(
                    "本期投入 %d 小时，收入 ¥%.2f，支出 ¥%.2f。",
                    totalTime / 60, totalIncome / 100.0, totalExpense / 100.0);
        }
        Double incomeChangeRatio = ratioChange(totalIncome, prevIncome);
        Double expenseChangeRatio = ratioChange(totalExpense, prevExpense);
        Double workChangeRatio = ratioChange(totalWorkMinutes, prevWorkMinutes);

        return new ReviewReport(periodName, summary, totalTime, totalWorkMinutes, totalIncome, totalExpense,
                prevIncome, prevExpense, prevWorkMinutes, incomeChangeRatio, expenseChangeRatio, workChangeRatio,
                actualHourlyRateCents, idealHourlyRateCents, timeDebtCents, passiveCoverRatio, freedomDelta,
                currentFreedomPercentage, allocations, topProjects, sinkholeProjects, keyEvents, incomeHistory,
                historyRecords, timeTagMetrics, expenseTagMetrics);
    }

    private static Double ratioChange(long current, long previous) {
        if (previous <= 0) {
            return null;
        }
        return ((double) current - (double) previous) / (double) previous;
    }

    private List<RecentRecordItem> fetchKeyEvents(SQLiteDatabase db, String start, String end) {
        String userId = userContext.requireCurrentUserId();
        List<RecentRecordItem> evts = new ArrayList<>();
        // Fetch top 2 biggest expenses
        String expSql = "SELECT amount_cents, category, updated_at, notes FROM expense WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0 ORDER BY amount_cents DESC LIMIT 2";
        try (Cursor c = db.rawQuery(expSql, new String[] { userId, start, end })) {
            while (c.moveToNext()) {
                evts.add(new RecentRecordItem("expense", c.getString(2), "Big Expense: ¥" + (c.getLong(0) / 100.0),
                        c.getString(1) + " " + c.getString(3)));
            }
        }
        // Fetch top 2 longest time logs
        String tSql = "SELECT duration_minutes, category, updated_at, notes FROM time_log WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0 ORDER BY duration_minutes DESC LIMIT 2";
        try (Cursor c = db.rawQuery(tSql, new String[] { userId, start, end })) {
            while (c.moveToNext()) {
                evts.add(new RecentRecordItem("time_log", c.getString(2), "Deep Work: " + c.getLong(0) + "m",
                        c.getString(1) + " " + c.getString(3)));
            }
        }
        return evts;
    }

    private List<RecentRecordItem> fetchIncomeHistory(SQLiteDatabase db, String userId, String start, String end) {
        List<RecentRecordItem> rows = new ArrayList<>();
        String sql = "SELECT occurred_on, source_name, amount_cents, type, note " +
                "FROM income WHERE owner_user_id = ? AND updated_at >= ? AND updated_at <= ? AND is_deleted = 0 " +
                "ORDER BY occurred_on DESC, created_at DESC LIMIT 120";
        try (Cursor c = db.rawQuery(sql, new String[] { userId, start, end })) {
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

    private List<RecentRecordItem> fetchHistoryRecords(SQLiteDatabase db, String userId, String start, String end) {
        List<RecentRecordItem> rows = new ArrayList<>();
        String sql = "SELECT type, occurred_at, title, detail FROM (" +
                "SELECT 'time' AS type, t.updated_at AS occurred_at, t.category AS title, COALESCE(t.note, '') AS detail FROM time_log t " +
                "WHERE t.owner_user_id = ? AND t.updated_at >= ? AND t.updated_at <= ? AND t.is_deleted = 0 " +
                "UNION ALL " +
                "SELECT 'income' AS type, i.occurred_on || 'T00:00:00Z' AS occurred_at, i.source_name AS title, i.amount_cents || ' cents' AS detail FROM income i " +
                "WHERE i.owner_user_id = ? AND i.updated_at >= ? AND i.updated_at <= ? AND i.is_deleted = 0 " +
                "UNION ALL " +
                "SELECT 'expense' AS type, e.occurred_on || 'T00:00:00Z' AS occurred_at, e.category AS title, e.amount_cents || ' cents' AS detail FROM expense e " +
                "WHERE e.owner_user_id = ? AND e.updated_at >= ? AND e.updated_at <= ? AND e.is_deleted = 0 " +
                "UNION ALL " +
                "SELECT 'learning' AS type, l.occurred_on || 'T00:00:00Z' AS occurred_at, l.content AS title, l.duration_minutes || ' min' AS detail FROM learning_record l " +
                "WHERE l.owner_user_id = ? AND l.updated_at >= ? AND l.updated_at <= ? AND l.is_deleted = 0" +
                ") ORDER BY occurred_at DESC LIMIT 200";
        String[] args = new String[] { userId, start, end, userId, start, end, userId, start, end, userId, start, end };
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

    private List<ReviewReport.TagMetric> fetchTimeTagMetrics(SQLiteDatabase db, String userId, String start, String end) {
        List<ReviewReport.TagMetric> result = new ArrayList<>();
        String sql = "SELECT tg.name, COALESCE(tg.emoji,''), SUM(tl.duration_minutes) AS total_minutes " +
                "FROM time_log_tag tlt " +
                "JOIN time_log tl ON tl.id = tlt.time_log_id " +
                "JOIN tag tg ON tg.id = tlt.tag_id " +
                "WHERE tl.owner_user_id = ? AND tl.updated_at >= ? AND tl.updated_at <= ? AND tl.is_deleted = 0 AND tg.is_active = 1 " +
                "GROUP BY tg.id, tg.name, tg.emoji " +
                "ORDER BY total_minutes DESC LIMIT 8";
        long sum = 0L;
        List<Object[]> rows = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, new String[] { userId, start, end })) {
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

    private List<ReviewReport.TagMetric> fetchExpenseTagMetrics(SQLiteDatabase db, String userId, String start, String end) {
        List<ReviewReport.TagMetric> result = new ArrayList<>();
        String sql = "SELECT tg.name, COALESCE(tg.emoji,''), SUM(e.amount_cents) AS total_cents " +
                "FROM expense_tag et " +
                "JOIN expense e ON e.id = et.expense_id " +
                "JOIN tag tg ON tg.id = et.tag_id " +
                "WHERE e.owner_user_id = ? AND e.updated_at >= ? AND e.updated_at <= ? AND e.is_deleted = 0 AND tg.is_active = 1 " +
                "GROUP BY tg.id, tg.name, tg.emoji " +
                "ORDER BY total_cents DESC LIMIT 8";
        long sum = 0L;
        List<Object[]> rows = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, new String[] { userId, start, end })) {
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

    private long scalarLong(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return 0L;
            }
            return cursor.getLong(0);
        }
    }
}
