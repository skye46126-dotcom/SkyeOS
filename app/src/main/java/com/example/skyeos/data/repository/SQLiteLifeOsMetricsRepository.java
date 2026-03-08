package com.example.skyeos.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SQLiteLifeOsMetricsRepository implements LifeOsMetricsRepository {
    private static final Set<String> WINDOW_TYPES = Set.of("day", "week", "month", "year");
    private final LifeOsDatabase database;
    private final CurrentUserContext userContext;

    public SQLiteLifeOsMetricsRepository(LifeOsDatabase database, CurrentUserContext userContext) {
        this.database = database;
        this.userContext = userContext;
    }

    @Override
    public MetricSnapshotSummary recomputeSnapshot(String snapshotDate, String windowType) {
        String date = normalizeDate(snapshotDate);
        String window = normalizeWindow(windowType);
        String userId = userContext.requireCurrentUserId();
        String timezone = queryTimezone();
        WindowRange range = WindowRange.from(date, window, timezone);

        long totalIncome = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId,
                range.startDate,
                range.endDate
        );
        long totalExpense = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM expense WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId,
                range.startDate,
                range.endDate
        );
        long structuralExpense = structuralExpenseForWindow(userId, range.startDate, range.endDate, false);
        totalExpense += structuralExpense;
        long passiveIncome = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND is_passive = 1 AND occurred_on >= ? AND occurred_on <= ?",
                userId,
                range.startDate,
                range.endDate
        );
        long necessaryExpense = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM expense WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'necessary' AND occurred_on >= ? AND occurred_on <= ?",
                userId,
                range.startDate,
                range.endDate
        );
        long structuralNecessaryExpense = structuralExpenseForWindow(userId, range.startDate, range.endDate, true);
        necessaryExpense += structuralNecessaryExpense;
        long totalWorkMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
                userId,
                range.startAtUtc,
                range.endAtUtcExclusive
        );
        long idealHourlyRate = scalarLong(
                "SELECT COALESCE(ideal_hourly_rate_cents, 0) FROM users WHERE id = ? LIMIT 1",
                userId);

        Long hourlyRate = totalWorkMinutes > 0 ? (totalIncome * 60) / totalWorkMinutes : null;
        Long timeDebt = hourlyRate == null ? null : (idealHourlyRate - hourlyRate);
        Double passiveCoverRatio = necessaryExpense > 0 ? ((double) passiveIncome / (double) necessaryExpense) : null;
        long freedom = passiveIncome - necessaryExpense;

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        SQLiteDatabase db = database.writableDb();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("owner_user_id", userId);
        values.put("snapshot_date", date);
        values.put("window_type", window);
        if (hourlyRate != null) {
            values.put("hourly_rate_cents", hourlyRate);
        }
        if (timeDebt != null) {
            values.put("time_debt_cents", timeDebt);
        }
        if (passiveCoverRatio != null) {
            values.put("passive_cover_ratio", passiveCoverRatio);
        }
        values.put("freedom_cents", freedom);
        values.put("total_income_cents", totalIncome);
        values.put("total_expense_cents", totalExpense);
        values.put("total_work_minutes", totalWorkMinutes);
        values.put("generated_at", now);

        db.insertWithOnConflict("metric_snapshot", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return getSnapshot(date, window);
    }

    @Override
    public MetricSnapshotSummary getSnapshot(String snapshotDate, String windowType) {
        String date = normalizeDate(snapshotDate);
        String window = normalizeWindow(windowType);
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery(
                "SELECT id,snapshot_date,window_type,hourly_rate_cents,time_debt_cents,passive_cover_ratio,freedom_cents,total_income_cents,total_expense_cents,total_work_minutes,generated_at " +
                        "FROM metric_snapshot WHERE owner_user_id = ? AND snapshot_date = ? AND window_type = ? LIMIT 1",
                new String[]{userId, date, window}
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return mapSnapshot(cursor);
        }
    }

    @Override
    public MetricSnapshotSummary getLatestSnapshot(String windowType) {
        String window = normalizeWindow(windowType);
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery(
                "SELECT id,snapshot_date,window_type,hourly_rate_cents,time_debt_cents,passive_cover_ratio,freedom_cents,total_income_cents,total_expense_cents,total_work_minutes,generated_at " +
                        "FROM metric_snapshot WHERE owner_user_id = ? AND window_type = ? ORDER BY snapshot_date DESC LIMIT 1",
                new String[]{userId, window}
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return mapSnapshot(cursor);
        }
    }

    @Override
    public long getIdealHourlyRateCents() {
        return scalarLong(
                "SELECT COALESCE(ideal_hourly_rate_cents, 0) FROM users WHERE id = ? LIMIT 1",
                userContext.requireCurrentUserId());
    }

    @Override
    public void setIdealHourlyRateCents(long cents) {
        long value = Math.max(0L, cents);
        SQLiteDatabase db = database.writableDb();
        ContentValues values = new ContentValues();
        values.put("ideal_hourly_rate_cents", value);
        values.put("updated_at", Instant.now().toString());
        String userId = userContext.requireCurrentUserId();
        int updated = db.update("users", values, "id = ?", new String[] { userId });
        if (updated <= 0) {
            values.put("id", userId);
            values.put("username", "owner");
            values.put("display_name", "Owner");
            values.put("password_hash", "__SET_ME__");
            values.put("status", "active");
            values.put("timezone", "Asia/Shanghai");
            values.put("currency_code", "CNY");
            values.put("created_at", Instant.now().toString());
            db.insertWithOnConflict("users", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    @Override
    public long getCurrentMonthBasicLivingCents() {
        String userId = userContext.requireCurrentUserId();
        String month = YearMonth.now().toString();
        return scalarLong(
                "SELECT COALESCE(basic_living_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                userId, month);
    }

    @Override
    public void setCurrentMonthBasicLivingCents(long cents) {
        upsertCurrentMonthBaseline(Math.max(0L, cents), null);
    }

    @Override
    public long getCurrentMonthFixedSubscriptionCents() {
        String userId = userContext.requireCurrentUserId();
        String month = YearMonth.now().toString();
        return scalarLong(
                "SELECT COALESCE(fixed_subscription_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                userId, month);
    }

    @Override
    public void setCurrentMonthFixedSubscriptionCents(long cents) {
        upsertCurrentMonthBaseline(null, Math.max(0L, cents));
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
            return "Asia/Shanghai";
        }
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

    private void upsertCurrentMonthBaseline(Long basicLivingCents, Long fixedSubscriptionCents) {
        String userId = userContext.requireCurrentUserId();
        String month = YearMonth.now().toString();
        SQLiteDatabase db = database.writableDb();
        String now = Instant.now().toString();
        db.beginTransaction();
        try {
            long currentBasic = scalarLong(
                    "SELECT COALESCE(basic_living_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                    userId, month);
            long currentFixed = scalarLong(
                    "SELECT COALESCE(fixed_subscription_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                    userId, month);
            ContentValues values = new ContentValues();
            values.put("owner_user_id", userId);
            values.put("month", month);
            values.put("basic_living_cents", basicLivingCents != null ? basicLivingCents : currentBasic);
            values.put("fixed_subscription_cents", fixedSubscriptionCents != null ? fixedSubscriptionCents : currentFixed);
            values.put("updated_at", now);
            values.put("created_at", now);
            db.insertWithOnConflict("expense_baseline_month", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private long structuralExpenseForWindow(String userId, String startDate, String endDate, boolean necessaryOnly) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (end.isBefore(start)) {
            return 0L;
        }
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
                long baselineMonthly = baselineMonthlyCost(userId, cursor.toString(), necessaryOnly);
                long recurringMonthly = recurringMonthlyCost(userId, cursor.toString(), necessaryOnly);
                long capexMonthly = capexMonthlyCost(userId, cursor.toString(), necessaryOnly);
                long monthTotal = baselineMonthly + recurringMonthly + capexMonthly;
                total += monthTotal * overlapDays / monthDays;
            }
            cursor = cursor.plusMonths(1);
        }
        return total;
    }

    private long baselineMonthlyCost(String userId, String month, boolean necessaryOnly) {
        if (necessaryOnly) {
            return scalarLong(
                    "SELECT COALESCE(basic_living_cents, 0) + COALESCE(fixed_subscription_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                    userId, month);
        }
        return scalarLong(
                "SELECT COALESCE(basic_living_cents, 0) + COALESCE(fixed_subscription_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                userId, month);
    }

    private long recurringMonthlyCost(String userId, String month, boolean necessaryOnly) {
        if (necessaryOnly) {
            return scalarLong(
                    "SELECT COALESCE(SUM(monthly_amount_cents), 0) FROM expense_recurring_rule " +
                            "WHERE owner_user_id = ? AND is_active = 1 AND is_necessary = 1 AND start_month <= ? AND (end_month IS NULL OR end_month = '' OR end_month >= ?)",
                    userId, month, month);
        }
        return scalarLong(
                "SELECT COALESCE(SUM(monthly_amount_cents), 0) FROM expense_recurring_rule " +
                        "WHERE owner_user_id = ? AND is_active = 1 AND start_month <= ? AND (end_month IS NULL OR end_month = '' OR end_month >= ?)",
                userId, month, month);
    }

    private long capexMonthlyCost(String userId, String month, boolean necessaryOnly) {
        if (necessaryOnly) {
            return 0L;
        }
        return scalarLong(
                "SELECT COALESCE(SUM(monthly_amortized_cents), 0) FROM expense_capex " +
                        "WHERE owner_user_id = ? AND is_active = 1 AND amortization_start_month <= ? AND amortization_end_month >= ?",
                userId, month, month);
    }

    private static MetricSnapshotSummary mapSnapshot(Cursor cursor) {
        return new MetricSnapshotSummary(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.isNull(3) ? null : cursor.getLong(3),
                cursor.isNull(4) ? null : cursor.getLong(4),
                cursor.isNull(5) ? null : cursor.getDouble(5),
                cursor.isNull(6) ? null : cursor.getLong(6),
                cursor.isNull(7) ? null : cursor.getLong(7),
                cursor.isNull(8) ? null : cursor.getLong(8),
                cursor.isNull(9) ? null : cursor.getLong(9),
                cursor.getString(10)
        );
    }

    private static String normalizeDate(String snapshotDate) {
        if (TextUtils.isEmpty(snapshotDate)) {
            return LocalDate.now().toString();
        }
        return LocalDate.parse(snapshotDate.trim()).toString();
    }

    private static String normalizeWindow(String windowType) {
        String value = TextUtils.isEmpty(windowType) ? "day" : windowType.trim().toLowerCase(Locale.US);
        if (!WINDOW_TYPES.contains(value)) {
            throw new IllegalArgumentException("Invalid windowType: " + value);
        }
        return value;
    }

    private static final class WindowRange {
        final String startDate;
        final String endDate;
        final String startAtUtc;
        final String endAtUtcExclusive;

        private WindowRange(String startDate, String endDate, String startAtUtc, String endAtUtcExclusive) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.startAtUtc = startAtUtc;
            this.endAtUtcExclusive = endAtUtcExclusive;
        }

        static WindowRange from(String snapshotDate, String windowType, String timezone) {
            ZoneId zoneId = ZoneId.of(timezone);
            LocalDate anchor = LocalDate.parse(snapshotDate);
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
            ZonedDateTime zStart = ZonedDateTime.of(start, LocalTime.MIN, zoneId);
            ZonedDateTime zEndExclusive = ZonedDateTime.of(end.plusDays(1), LocalTime.MIN, zoneId);
            return new WindowRange(
                    start.toString(),
                    end.toString(),
                    zStart.toInstant().toString(),
                    zEndExclusive.toInstant().toString()
            );
        }
    }
}
