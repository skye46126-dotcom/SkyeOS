package com.example.skyeos.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.example.skyeos.data.auth.CurrentUserContext;
import com.example.skyeos.data.db.LifeOsDatabase;
import com.example.skyeos.domain.model.CapexCostSummary;
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.MonthlyCostBaseline;
import com.example.skyeos.domain.model.RateComparisonSummary;
import com.example.skyeos.domain.model.RecurringCostRuleSummary;
import com.example.skyeos.domain.repository.LifeOsMetricsRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
    public RateComparisonSummary getRateComparison(String anchorDate, String windowType) {
        String date = normalizeDate(anchorDate);
        String window = normalizeWindow(windowType);
        String userId = userContext.requireCurrentUserId();
        String timezone = queryTimezone();
        WindowRange range = WindowRange.from(date, window, timezone);

        long idealHourlyRate = getIdealHourlyRateCents();
        long currentIncome = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId, range.startDate, range.endDate);
        long currentWorkMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
                userId, range.startAtUtc, range.endAtUtcExclusive);
        Long actualHourlyRate = currentWorkMinutes > 0 ? (currentIncome * 60) / currentWorkMinutes : null;

        int previousYear = LocalDate.parse(date).minusYears(1).getYear();
        String previousYearStart = String.format(Locale.US, "%04d-01-01", previousYear);
        String previousYearEnd = String.format(Locale.US, "%04d-12-31", previousYear);
        String previousYearStartAtUtc = toUtcStart(previousYearStart, timezone);
        String previousYearEndAtUtcExclusive = toUtcEndExclusive(previousYearEnd, timezone);
        long previousYearIncome = scalarLong(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
                userId, previousYearStart, previousYearEnd);
        long previousYearWorkMinutes = scalarLong(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
                userId, previousYearStartAtUtc, previousYearEndAtUtcExclusive);
        Long previousYearAverageHourlyRate = previousYearWorkMinutes > 0
                ? (previousYearIncome * 60) / previousYearWorkMinutes
                : null;

        return new RateComparisonSummary(
                date,
                window,
                idealHourlyRate,
                previousYearAverageHourlyRate,
                actualHourlyRate,
                previousYearIncome,
                previousYearWorkMinutes,
                currentIncome,
                currentWorkMinutes);
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

    @Override
    public MonthlyCostBaseline getMonthlyBaseline(String month) {
        String normalizedMonth = normalizeMonth(month);
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(basic_living_cents, 0), COALESCE(fixed_subscription_cents, 0) " +
                        "FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
                new String[]{userId, normalizedMonth})) {
            if (!cursor.moveToFirst()) {
                return new MonthlyCostBaseline(normalizedMonth, 0L, 0L);
            }
            return new MonthlyCostBaseline(
                    normalizedMonth,
                    cursor.isNull(0) ? 0L : cursor.getLong(0),
                    cursor.isNull(1) ? 0L : cursor.getLong(1));
        }
    }

    @Override
    public void upsertMonthlyBaseline(String month, long basicLivingCents, long fixedSubscriptionCents) {
        upsertBaselineForMonth(
                normalizeMonth(month),
                Math.max(0L, basicLivingCents),
                Math.max(0L, fixedSubscriptionCents));
    }

    @Override
    public List<RecurringCostRuleSummary> listRecurringCostRules() {
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        List<RecurringCostRuleSummary> items = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, name, category, monthly_amount_cents, is_necessary, start_month, end_month, is_active, COALESCE(note,'') " +
                        "FROM expense_recurring_rule WHERE owner_user_id = ? ORDER BY is_active DESC, start_month DESC, updated_at DESC",
                new String[]{userId})) {
            while (cursor.moveToNext()) {
                items.add(new RecurringCostRuleSummary(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getInt(4) == 1,
                        cursor.getString(5),
                        cursor.isNull(6) ? null : cursor.getString(6),
                        cursor.getInt(7) == 1,
                        cursor.getString(8)));
            }
        }
        return items;
    }

    @Override
    public void createRecurringCostRule(String name, String category, long monthlyAmountCents, boolean isNecessary,
            String startMonth, String endMonth, String note) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(name.trim())) {
            throw new IllegalArgumentException("Recurring cost name is required");
        }
        String normalizedCategory = normalizeExpenseCategory(category);
        String normalizedStartMonth = normalizeMonth(startMonth);
        String normalizedEndMonth = TextUtils.isEmpty(endMonth) ? null : normalizeMonth(endMonth);
        if (normalizedEndMonth != null && normalizedEndMonth.compareTo(normalizedStartMonth) < 0) {
            throw new IllegalArgumentException("endMonth must be >= startMonth");
        }
        long safeAmount = Math.max(0L, monthlyAmountCents);
        String userId = userContext.requireCurrentUserId();
        String now = Instant.now().toString();
        ContentValues values = new ContentValues();
        values.put("id", UUID.randomUUID().toString());
        values.put("owner_user_id", userId);
        values.put("name", name.trim());
        values.put("category", normalizedCategory);
        values.put("monthly_amount_cents", safeAmount);
        values.put("is_necessary", isNecessary ? 1 : 0);
        values.put("start_month", normalizedStartMonth);
        values.put("end_month", normalizedEndMonth);
        values.put("is_active", 1);
        values.put("note", TextUtils.isEmpty(note) ? null : note.trim());
        values.put("created_at", now);
        values.put("updated_at", now);
        database.writableDb().insertOrThrow("expense_recurring_rule", null, values);
    }

    @Override
    public List<CapexCostSummary> listCapexCosts() {
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.readableDb();
        List<CapexCostSummary> items = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, name, purchase_date, purchase_amount_cents, monthly_amortized_cents, amortization_start_month, amortization_end_month, is_active, COALESCE(note,'') " +
                        "FROM expense_capex WHERE owner_user_id = ? ORDER BY is_active DESC, purchase_date DESC, updated_at DESC",
                new String[]{userId})) {
            while (cursor.moveToNext()) {
                items.add(new CapexCostSummary(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getLong(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getInt(7) == 1,
                        cursor.getString(8)));
            }
        }
        return items;
    }

    @Override
    public void createCapexCost(String name, String purchaseDate, long purchaseAmountCents, int usefulMonths,
            int residualRateBps, String note) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(name.trim())) {
            throw new IllegalArgumentException("Capex name is required");
        }
        LocalDate normalizedPurchaseDate = LocalDate.parse(purchaseDate == null ? LocalDate.now().toString() : purchaseDate.trim());
        if (usefulMonths <= 0) {
            throw new IllegalArgumentException("usefulMonths must be > 0");
        }
        int safeResidualRateBps = Math.max(0, Math.min(10000, residualRateBps));
        long safePurchaseAmount = Math.max(0L, purchaseAmountCents);
        long residualCents = Math.round(safePurchaseAmount * (safeResidualRateBps / 10000.0));
        long amortizableCents = Math.max(0L, safePurchaseAmount - residualCents);
        long monthlyAmortizedCents = usefulMonths <= 0 ? 0L : Math.round(amortizableCents / (double) usefulMonths);
        YearMonth startMonth = YearMonth.from(normalizedPurchaseDate);
        YearMonth endMonth = startMonth.plusMonths(Math.max(0, usefulMonths - 1L));
        String userId = userContext.requireCurrentUserId();
        String now = Instant.now().toString();
        ContentValues values = new ContentValues();
        values.put("id", UUID.randomUUID().toString());
        values.put("owner_user_id", userId);
        values.put("name", name.trim());
        values.put("purchase_date", normalizedPurchaseDate.toString());
        values.put("purchase_amount_cents", safePurchaseAmount);
        values.put("residual_rate_bps", safeResidualRateBps);
        values.put("useful_months", usefulMonths);
        values.put("monthly_amortized_cents", monthlyAmortizedCents);
        values.put("amortization_start_month", startMonth.toString());
        values.put("amortization_end_month", endMonth.toString());
        values.put("is_active", 1);
        values.put("note", TextUtils.isEmpty(note) ? null : note.trim());
        values.put("created_at", now);
        values.put("updated_at", now);
        database.writableDb().insertOrThrow("expense_capex", null, values);
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
        String month = YearMonth.now().toString();
        MonthlyCostBaseline current = getMonthlyBaseline(month);
        upsertBaselineForMonth(
                month,
                basicLivingCents != null ? basicLivingCents : current.basicLivingCents,
                fixedSubscriptionCents != null ? fixedSubscriptionCents : current.fixedSubscriptionCents);
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

    private String normalizeMonth(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return YearMonth.now().toString();
        }
        return YearMonth.parse(raw.trim()).toString();
    }

    private void upsertBaselineForMonth(String month, long basicLivingCents, long fixedSubscriptionCents) {
        String userId = userContext.requireCurrentUserId();
        SQLiteDatabase db = database.writableDb();
        String now = Instant.now().toString();
        ContentValues values = new ContentValues();
        values.put("owner_user_id", userId);
        values.put("month", month);
        values.put("basic_living_cents", Math.max(0L, basicLivingCents));
        values.put("fixed_subscription_cents", Math.max(0L, fixedSubscriptionCents));
        values.put("updated_at", now);
        values.put("created_at", now);
        db.insertWithOnConflict("expense_baseline_month", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String normalizeExpenseCategory(String raw) {
        String value = TextUtils.isEmpty(raw) ? "necessary" : raw.trim().toLowerCase(Locale.US);
        if (!Set.of("necessary", "experience", "subscription", "investment").contains(value)) {
            throw new IllegalArgumentException("Invalid expense category: " + value);
        }
        return value;
    }

    private static String toUtcStart(String date, String timezone) {
        ZoneId zoneId = ZoneId.of(TextUtils.isEmpty(timezone) ? "Asia/Shanghai" : timezone);
        LocalDate localDate = LocalDate.parse(date);
        return ZonedDateTime.of(localDate, LocalTime.MIN, zoneId).toInstant().toString();
    }

    private static String toUtcEndExclusive(String date, String timezone) {
        ZoneId zoneId = ZoneId.of(TextUtils.isEmpty(timezone) ? "Asia/Shanghai" : timezone);
        LocalDate localDate = LocalDate.parse(date).plusDays(1);
        return ZonedDateTime.of(localDate, LocalTime.MIN, zoneId).toInstant().toString();
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
