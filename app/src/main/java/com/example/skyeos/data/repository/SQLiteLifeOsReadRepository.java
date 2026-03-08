package com.example.skyeos.data.repository;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.domain.model.ProjectOption;
import com.example.skyeos.domain.model.RecentRecordItem;
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

    public SQLiteLifeOsReadRepository(LifeOsDatabase database) {
        this.database = database;
    }

    @Override
    public WindowOverview getOverview(String anchorDate, String windowType) {
        String date = normalizeDate(anchorDate);
        String window = normalizeWindow(windowType);
        DateRange range = DateRange.from(date, window);

        long totalIncome = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                range.startDate, range.endDate
        );
        long totalExpense = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM expense WHERE is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                range.startDate, range.endDate
        );
        long totalWorkMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE is_deleted = 0 AND category = 'work' AND date(started_at) >= ? AND date(started_at) <= ?",
                range.startDate, range.endDate
        );
        long totalTimeMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE is_deleted = 0 AND date(started_at) >= ? AND date(started_at) <= ?",
                range.startDate, range.endDate
        );
        long publicTimeMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE is_deleted = 0 AND is_public_pool = 1 AND date(started_at) >= ? AND date(started_at) <= ?",
                range.startDate, range.endDate
        );
        long publicIncome = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE is_deleted = 0 AND is_public_pool = 1 AND occurred_on >= ? AND occurred_on <= ?",
                range.startDate, range.endDate
        );
        long totalLearningMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM learning_record WHERE is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                range.startDate, range.endDate
        );
        long publicLearningMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM learning_record WHERE is_deleted = 0 AND is_public_pool = 1 AND occurred_on >= ? AND occurred_on <= ?",
                range.startDate, range.endDate
        );

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
                totalExpense,
                netIncome,
                totalWorkMinutes,
                totalTimeMinutes,
                publicTimeRatio,
                publicIncomeRatio,
                publicLearningRatio
        );
    }

    @Override
    public List<RecentRecordItem> getRecentRecords(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        SQLiteDatabase db = database.readableDb();
        String sql =
                "SELECT type, occurred_at, title, detail FROM (" +
                        "SELECT 'time' AS type, started_at AS occurred_at, category AS title, COALESCE(note, '') AS detail FROM time_log WHERE is_deleted = 0 " +
                        "UNION ALL " +
                        "SELECT 'income' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, source_name AS title, amount_cents || ' cents' AS detail FROM income WHERE is_deleted = 0 " +
                        "UNION ALL " +
                        "SELECT 'expense' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, category AS title, amount_cents || ' cents' AS detail FROM expense WHERE is_deleted = 0 " +
                        "UNION ALL " +
                        "SELECT 'learning' AS type, occurred_on || 'T00:00:00Z' AS occurred_at, content AS title, duration_minutes || ' min' AS detail FROM learning_record WHERE is_deleted = 0" +
                        ") ORDER BY occurred_at DESC LIMIT " + safeLimit;
        List<RecentRecordItem> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                result.add(new RecentRecordItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3)
                ));
            }
        }
        return result;
    }

    @Override
    public List<ProjectOption> getProjectOptions(boolean includeDone) {
        SQLiteDatabase db = database.readableDb();
        String sql = includeDone
                ? "SELECT id, name, status FROM project WHERE is_deleted = 0 ORDER BY updated_at DESC"
                : "SELECT id, name, status FROM project WHERE is_deleted = 0 AND status != 'done' ORDER BY updated_at DESC";
        List<ProjectOption> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                result.add(new ProjectOption(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2)
                ));
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

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / (double) denominator;
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
