package com.example.skyeos.data.repository

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.skyeos.data.auth.CurrentUserContext
import com.example.skyeos.data.db.LifeOsDatabase
import com.example.skyeos.data.db.getLongOrNull
import com.example.skyeos.data.db.getStringOrNull
import com.example.skyeos.domain.model.CapexCostSummary
import com.example.skyeos.domain.model.MetricSnapshotSummary
import com.example.skyeos.domain.model.MonthlyCostBaseline
import com.example.skyeos.domain.model.RateComparisonSummary
import com.example.skyeos.domain.model.RecurringCostRuleSummary
import com.example.skyeos.domain.repository.LifeOsMetricsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class SQLiteLifeOsMetricsRepository @Inject constructor(
    private val database: LifeOsDatabase,
    private val userContext: CurrentUserContext
) : LifeOsMetricsRepository {

    companion object {
        private val WINDOW_TYPES = setOf("day", "week", "month", "year")

        private fun normalizeDate(snapshotDate: String?): String {
            if (snapshotDate.isNullOrBlank()) return LocalDate.now().toString()
            return LocalDate.parse(snapshotDate.trim()).toString()
        }

        private fun normalizeWindow(windowType: String?): String {
            val value = windowType?.trim()?.lowercase(Locale.US).takeIf { !it.isNullOrEmpty() } ?: "day"
            require(WINDOW_TYPES.contains(value)) { "Invalid windowType: $value" }
            return value
        }

        private fun normalizeMonth(raw: String?): String {
            if (raw.isNullOrBlank()) return YearMonth.now().toString()
            return YearMonth.parse(raw.trim()).toString()
        }

        private fun normalizeRecurringCategory(raw: String?): String {
            val value = raw?.trim()?.lowercase(Locale.US).orEmpty()
            return if (value.isEmpty()) "subscription" else value
        }

        private fun toUtcStart(date: String, timezone: String): String {
            val zoneId = ZoneId.of(if (timezone.isBlank()) "Asia/Shanghai" else timezone)
            val localDate = LocalDate.parse(date)
            return ZonedDateTime.of(localDate, LocalTime.MIN, zoneId).toInstant().toString()
        }

        private fun toUtcEndExclusive(date: String, timezone: String): String {
            val zoneId = ZoneId.of(if (timezone.isBlank()) "Asia/Shanghai" else timezone)
            val localDate = LocalDate.parse(date).plusDays(1)
            return ZonedDateTime.of(localDate, LocalTime.MIN, zoneId).toInstant().toString()
        }

        private fun mapSnapshot(cursor: Cursor): MetricSnapshotSummary {
            return MetricSnapshotSummary(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getLongOrNull(3),
                cursor.getLongOrNull(4),
                if (cursor.isNull(5)) null else cursor.getDouble(5),
                cursor.getLongOrNull(6),
                cursor.getLongOrNull(7),
                cursor.getLongOrNull(8),
                cursor.getLongOrNull(9),
                cursor.getString(10)
            )
        }
    }

    private data class WindowRange(
        val startDate: String,
        val endDate: String,
        val startAtUtc: String,
        val endAtUtcExclusive: String
    ) {
        companion object {
            fun from(snapshotDate: String, windowType: String, timezone: String): WindowRange {
                val zoneId = ZoneId.of(timezone)
                val anchor = LocalDate.parse(snapshotDate)
                val (start, end) = when (windowType) {
                    "day" -> anchor to anchor
                    "week" -> anchor.minusDays(6) to anchor
                    "month" -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
                    "year" -> anchor.withDayOfYear(1) to anchor.withDayOfYear(anchor.lengthOfYear())
                    else -> throw IllegalArgumentException("Unsupported windowType: $windowType")
                }
                val zStart = ZonedDateTime.of(start, LocalTime.MIN, zoneId)
                val zEndExclusive = ZonedDateTime.of(end.plusDays(1), LocalTime.MIN, zoneId)
                return WindowRange(
                    start.toString(),
                    end.toString(),
                    zStart.toInstant().toString(),
                    zEndExclusive.toInstant().toString()
                )
            }
        }
    }

    override fun recomputeSnapshot(snapshotDate: String?, windowType: String?): MetricSnapshotSummary {
        val date = normalizeDate(snapshotDate)
        val window = normalizeWindow(windowType)
        val userId = userContext.requireCurrentUserId()
        val timezone = queryTimezone()
        val range = WindowRange.from(date, window, timezone)

        val totalIncome = scalarLong(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
            userId, range.startDate, range.endDate
        )
        val totalExpenseRaw = scalarLong(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM expense WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
            userId, range.startDate, range.endDate
        )
        val structuralExpense = structuralExpenseForWindow(userId, range.startDate, range.endDate, false)
        val totalExpense = totalExpenseRaw + structuralExpense

        val passiveIncome = scalarLong(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND is_passive = 1 AND occurred_on >= ? AND occurred_on <= ?",
            userId, range.startDate, range.endDate
        )
        val necessaryExpenseRaw = scalarLong(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM expense WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'necessary' AND occurred_on >= ? AND occurred_on <= ?",
            userId, range.startDate, range.endDate
        )
        val structuralNecessaryExpense = structuralExpenseForWindow(userId, range.startDate, range.endDate, true)
        val necessaryExpense = necessaryExpenseRaw + structuralNecessaryExpense

        val totalWorkMinutes = scalarLong(
            "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
            userId, range.startAtUtc, range.endAtUtcExclusive
        )
        val idealHourlyRate = scalarLong(
            "SELECT COALESCE(ideal_hourly_rate_cents, 0) FROM users WHERE id = ? LIMIT 1",
            userId
        )

        val hourlyRate = if (totalWorkMinutes > 0) (totalIncome * 60) / totalWorkMinutes else null
        val timeDebt = if (hourlyRate != null) (idealHourlyRate - hourlyRate) else null
        val passiveCoverRatio = if (necessaryExpense > 0) (passiveIncome.toDouble() / necessaryExpense.toDouble()) else null
        val freedom = passiveIncome - necessaryExpense

        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()

        val values = ContentValues().apply {
            put("id", id)
            put("owner_user_id", userId)
            put("snapshot_date", date)
            put("window_type", window)
            hourlyRate?.let { put("hourly_rate_cents", it) }
            timeDebt?.let { put("time_debt_cents", it) }
            passiveCoverRatio?.let { put("passive_cover_ratio", it) }
            put("freedom_cents", freedom)
            put("total_income_cents", totalIncome)
            put("total_expense_cents", totalExpense)
            put("total_work_minutes", totalWorkMinutes)
            put("generated_at", now)
        }

        database.writableDb().insertWithOnConflict("metric_snapshot", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return getSnapshot(date, window)!!
    }

    override fun getSnapshot(snapshotDate: String?, windowType: String?): MetricSnapshotSummary? {
        val date = normalizeDate(snapshotDate)
        val window = normalizeWindow(windowType)
        val userId = userContext.requireCurrentUserId()

        return database.readableDb().rawQuery(
            """
            SELECT id,snapshot_date,window_type,hourly_rate_cents,time_debt_cents,passive_cover_ratio,freedom_cents,total_income_cents,total_expense_cents,total_work_minutes,generated_at 
            FROM metric_snapshot WHERE owner_user_id = ? AND snapshot_date = ? AND window_type = ? LIMIT 1
            """.trimIndent(),
            arrayOf(userId, date, window)
        ).use { cursor -> if (cursor.moveToFirst()) mapSnapshot(cursor) else null }
    }

    override fun getLatestSnapshot(windowType: String?): MetricSnapshotSummary? {
        val window = normalizeWindow(windowType)
        val userId = userContext.requireCurrentUserId()

        return database.readableDb().rawQuery(
            """
            SELECT id,snapshot_date,window_type,hourly_rate_cents,time_debt_cents,passive_cover_ratio,freedom_cents,total_income_cents,total_expense_cents,total_work_minutes,generated_at 
            FROM metric_snapshot WHERE owner_user_id = ? AND window_type = ? ORDER BY snapshot_date DESC LIMIT 1
            """.trimIndent(),
            arrayOf(userId, window)
        ).use { cursor -> if (cursor.moveToFirst()) mapSnapshot(cursor) else null }
    }

    override fun getRateComparison(anchorDate: String?, windowType: String?): RateComparisonSummary {
        val date = normalizeDate(anchorDate)
        val window = normalizeWindow(windowType)
        val userId = userContext.requireCurrentUserId()
        val timezone = queryTimezone()
        val range = WindowRange.from(date, window, timezone)

        val idealHourlyRate = idealHourlyRateCents
        val currentIncome = scalarLong(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
            userId, range.startDate, range.endDate
        )
        val currentWorkMinutes = scalarLong(
            "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
            userId, range.startAtUtc, range.endAtUtcExclusive
        )
        val actualHourlyRate = if (currentWorkMinutes > 0) (currentIncome * 60) / currentWorkMinutes else null

        val previousYear = LocalDate.parse(date).minusYears(1).year
        val previousYearStart = String.format(Locale.US, "%04d-01-01", previousYear)
        val previousYearEnd = String.format(Locale.US, "%04d-12-31", previousYear)
        val previousYearStartAtUtc = toUtcStart(previousYearStart, timezone)
        val previousYearEndAtUtcExclusive = toUtcEndExclusive(previousYearEnd, timezone)

        val previousYearIncome = scalarLong(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM income WHERE owner_user_id = ? AND is_deleted = 0 AND occurred_on >= ? AND occurred_on <= ?",
            userId, previousYearStart, previousYearEnd
        )
        val previousYearWorkMinutes = scalarLong(
            "SELECT COALESCE(SUM(duration_minutes), 0) FROM time_log WHERE owner_user_id = ? AND is_deleted = 0 AND category = 'work' AND started_at >= ? AND started_at < ?",
            userId, previousYearStartAtUtc, previousYearEndAtUtcExclusive
        )
        val previousYearAverageHourlyRate = if (previousYearWorkMinutes > 0) (previousYearIncome * 60) / previousYearWorkMinutes else null

        return RateComparisonSummary(
            date, window, idealHourlyRate, previousYearAverageHourlyRate, actualHourlyRate,
            previousYearIncome, previousYearWorkMinutes, currentIncome, currentWorkMinutes
        )
    }

    override fun getIdealHourlyRateCents(): Long {
        return scalarLong(
            "SELECT COALESCE(ideal_hourly_rate_cents, 0) FROM users WHERE id = ? LIMIT 1",
            userContext.requireCurrentUserId()
        )
    }

    override fun setIdealHourlyRateCents(cents: Long) {
        val value = cents.coerceAtLeast(0L)
        val userId = userContext.requireCurrentUserId()
        val now = Instant.now().toString()

        val values = ContentValues().apply {
            put("ideal_hourly_rate_cents", value)
            put("updated_at", now)
        }
        val updated = database.writableDb().update("users", values, "id = ?", arrayOf(userId))
        if (updated <= 0) {
            val insertValues = ContentValues(values).apply {
                put("id", userId)
                put("username", "owner")
                put("display_name", "Owner")
                put("password_hash", "__SET_ME__")
                put("status", "active")
                put("timezone", "Asia/Shanghai")
                put("currency_code", "CNY")
                put("created_at", now)
            }
            database.writableDb().insertWithOnConflict("users", null, insertValues, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override fun getCurrentMonthBasicLivingCents(): Long {
        return scalarLong(
            "SELECT COALESCE(basic_living_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
            userContext.requireCurrentUserId(), YearMonth.now().toString()
        )
    }

    override fun setCurrentMonthBasicLivingCents(cents: Long) {
        upsertCurrentMonthBaseline(cents.coerceAtLeast(0L), null)
    }

    override fun getCurrentMonthFixedSubscriptionCents(): Long {
        return scalarLong(
            "SELECT COALESCE(fixed_subscription_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
            userContext.requireCurrentUserId(), YearMonth.now().toString()
        )
    }

    override fun setCurrentMonthFixedSubscriptionCents(cents: Long) {
        upsertCurrentMonthBaseline(null, cents.coerceAtLeast(0L))
    }

    override fun getMonthlyBaseline(month: String?): MonthlyCostBaseline {
        val normalizedMonth = normalizeMonth(month)
        val userId = userContext.requireCurrentUserId()

        return database.readableDb().rawQuery(
            "SELECT COALESCE(basic_living_cents, 0), COALESCE(fixed_subscription_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1",
            arrayOf(userId, normalizedMonth)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                MonthlyCostBaseline(
                    normalizedMonth,
                    cursor.getLongOrNull(0) ?: 0L,
                    cursor.getLongOrNull(1) ?: 0L
                )
            } else {
                MonthlyCostBaseline(normalizedMonth, 0L, 0L)
            }
        }
    }

    override fun upsertMonthlyBaseline(month: String?, basicLivingCents: Long, fixedSubscriptionCents: Long) {
        upsertBaselineForMonth(
            normalizeMonth(month),
            basicLivingCents.coerceAtLeast(0L),
            fixedSubscriptionCents.coerceAtLeast(0L)
        )
    }

    override fun listRecurringCostRules(): List<RecurringCostRuleSummary> {
        val userId = userContext.requireCurrentUserId()
        val items = mutableListOf<RecurringCostRuleSummary>()

        database.readableDb().rawQuery(
            """
            SELECT id, name, category, monthly_amount_cents, is_necessary, start_month, end_month, is_active, COALESCE(note,'') 
            FROM expense_recurring_rule WHERE owner_user_id = ? ORDER BY is_active DESC, start_month DESC, updated_at DESC
            """.trimIndent(),
            arrayOf(userId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    RecurringCostRuleSummary(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getInt(4) == 1,
                        cursor.getString(5),
                        cursor.getStringOrNull(6),
                        cursor.getInt(7) == 1,
                        cursor.getString(8)
                    )
                )
            }
        }
        return items
    }

    override fun createRecurringCostRule(name: String?, category: String?, monthlyAmountCents: Long, isNecessary: Boolean, startMonth: String?, endMonth: String?, note: String?) {
        require(!name.isNullOrBlank()) { "Recurring cost name is required" }
        val normalizedStartMonth = normalizeMonth(startMonth)
        val normalizedEndMonth = if (endMonth.isNullOrBlank()) null else normalizeMonth(endMonth)
        if (normalizedEndMonth != null && normalizedEndMonth < normalizedStartMonth) {
            throw IllegalArgumentException("endMonth must be >= startMonth")
        }

        val userId = userContext.requireCurrentUserId()
        val now = Instant.now().toString()
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("owner_user_id", userId)
            put("name", name.trim())
            put("category", normalizeRecurringCategory(category))
            put("monthly_amount_cents", monthlyAmountCents.coerceAtLeast(0L))
            put("is_necessary", if (isNecessary) 1 else 0)
            put("start_month", normalizedStartMonth)
            put("end_month", normalizedEndMonth)
            put("is_active", 1)
            put("note", note?.trim()?.takeIf { it.isNotEmpty() })
            put("created_at", now)
            put("updated_at", now)
        }
        database.writableDb().insertOrThrow("expense_recurring_rule", null, values)
    }

    override fun updateRecurringCostRule(id: String?, name: String?, category: String?, monthlyAmountCents: Long, isNecessary: Boolean, startMonth: String?, endMonth: String?, note: String?) {
        require(!id.isNullOrBlank()) { "Recurring cost id is required" }
        require(!name.isNullOrBlank()) { "Recurring cost name is required" }
        val normalizedStartMonth = normalizeMonth(startMonth)
        val normalizedEndMonth = if (endMonth.isNullOrBlank()) null else normalizeMonth(endMonth)
        if (normalizedEndMonth != null && normalizedEndMonth < normalizedStartMonth) {
            throw IllegalArgumentException("endMonth must be >= startMonth")
        }

        val values = ContentValues().apply {
            put("name", name.trim())
            put("category", normalizeRecurringCategory(category))
            put("monthly_amount_cents", monthlyAmountCents.coerceAtLeast(0L))
            put("is_necessary", if (isNecessary) 1 else 0)
            put("start_month", normalizedStartMonth)
            put("end_month", normalizedEndMonth)
            put("note", note?.trim()?.takeIf { it.isNotEmpty() })
            put("updated_at", Instant.now().toString())
        }
        val updated = database.writableDb().update(
            "expense_recurring_rule", values, "id = ? AND owner_user_id = ?", arrayOf(id, userContext.requireCurrentUserId())
        )
        require(updated > 0) { "Recurring cost rule not found" }
    }

    override fun deleteRecurringCostRule(id: String?) {
        if (id.isNullOrBlank()) return
        database.writableDb().delete(
            "expense_recurring_rule", "id = ? AND owner_user_id = ?", arrayOf(id, userContext.requireCurrentUserId())
        )
    }

    override fun listCapexCosts(): List<CapexCostSummary> {
        val userId = userContext.requireCurrentUserId()
        val items = mutableListOf<CapexCostSummary>()
        database.readableDb().rawQuery(
            """
            SELECT id, name, purchase_date, purchase_amount_cents, useful_months, residual_rate_bps, monthly_amortized_cents, amortization_start_month, amortization_end_month, is_active, COALESCE(note,'') 
            FROM expense_capex WHERE owner_user_id = ? ORDER BY is_active DESC, purchase_date DESC, updated_at DESC
            """.trimIndent(),
            arrayOf(userId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    CapexCostSummary(
                        cursor.getString(0), cursor.getString(1), cursor.getString(2),
                        cursor.getLong(3), cursor.getInt(4), cursor.getInt(5),
                        cursor.getLong(6), cursor.getString(7), cursor.getString(8),
                        cursor.getInt(9) == 1, cursor.getString(10)
                    )
                )
            }
        }
        return items
    }

    override fun createCapexCost(name: String?, purchaseDate: String?, purchaseAmountCents: Long, usefulMonths: Int, residualRateBps: Int, note: String?) {
        require(!name.isNullOrBlank()) { "Capex name is required" }
        require(usefulMonths > 0) { "usefulMonths must be > 0" }

        val normalizedPurchaseDate = LocalDate.parse(if (purchaseDate.isNullOrBlank()) LocalDate.now().toString() else purchaseDate.trim())
        val safeResidualRateBps = residualRateBps.coerceIn(0, 10000)
        val safePurchaseAmount = purchaseAmountCents.coerceAtLeast(0L)
        val residualCents = Math.round(safePurchaseAmount * (safeResidualRateBps / 10000.0))
        val amortizableCents = (safePurchaseAmount - residualCents).coerceAtLeast(0L)
        val monthlyAmortizedCents = Math.round(amortizableCents / usefulMonths.toDouble())
        
        val startMonth = YearMonth.from(normalizedPurchaseDate)
        val endMonth = startMonth.plusMonths((usefulMonths - 1L).coerceAtLeast(0L))
        val userId = userContext.requireCurrentUserId()
        val now = Instant.now().toString()

        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("owner_user_id", userId)
            put("name", name.trim())
            put("purchase_date", normalizedPurchaseDate.toString())
            put("purchase_amount_cents", safePurchaseAmount)
            put("residual_rate_bps", safeResidualRateBps)
            put("useful_months", usefulMonths)
            put("monthly_amortized_cents", monthlyAmortizedCents)
            put("amortization_start_month", startMonth.toString())
            put("amortization_end_month", endMonth.toString())
            put("is_active", 1)
            put("note", note?.trim()?.takeIf { it.isNotEmpty() })
            put("created_at", now)
            put("updated_at", now)
        }
        database.writableDb().insertOrThrow("expense_capex", null, values)
    }

    override fun updateCapexCost(id: String?, name: String?, purchaseDate: String?, purchaseAmountCents: Long, usefulMonths: Int, residualRateBps: Int, note: String?) {
        require(!id.isNullOrBlank()) { "Capex id is required" }
        require(!name.isNullOrBlank()) { "Capex name is required" }
        require(usefulMonths > 0) { "usefulMonths must be > 0" }

        val normalizedPurchaseDate = LocalDate.parse(if (purchaseDate.isNullOrBlank()) LocalDate.now().toString() else purchaseDate.trim())
        val safeResidualRateBps = residualRateBps.coerceIn(0, 10000)
        val safePurchaseAmount = purchaseAmountCents.coerceAtLeast(0L)
        val residualCents = Math.round(safePurchaseAmount * (safeResidualRateBps / 10000.0))
        val amortizableCents = (safePurchaseAmount - residualCents).coerceAtLeast(0L)
        val monthlyAmortizedCents = Math.round(amortizableCents / usefulMonths.toDouble())

        val startMonth = YearMonth.from(normalizedPurchaseDate)
        val endMonth = startMonth.plusMonths((usefulMonths - 1L).coerceAtLeast(0L))

        val values = ContentValues().apply {
            put("name", name.trim())
            put("purchase_date", normalizedPurchaseDate.toString())
            put("purchase_amount_cents", safePurchaseAmount)
            put("residual_rate_bps", safeResidualRateBps)
            put("useful_months", usefulMonths)
            put("monthly_amortized_cents", monthlyAmortizedCents)
            put("amortization_start_month", startMonth.toString())
            put("amortization_end_month", endMonth.toString())
            put("note", note?.trim()?.takeIf { it.isNotEmpty() })
            put("updated_at", Instant.now().toString())
        }
        val updated = database.writableDb().update(
            "expense_capex", values, "id = ? AND owner_user_id = ?", arrayOf(id, userContext.requireCurrentUserId())
        )
        require(updated > 0) { "Capex item not found" }
    }

    override fun deleteCapexCost(id: String?) {
        if (id.isNullOrBlank()) return
        database.writableDb().delete(
            "expense_capex", "id = ? AND owner_user_id = ?", arrayOf(id, userContext.requireCurrentUserId())
        )
    }

    private fun queryTimezone(): String {
        return database.readableDb().rawQuery(
            "SELECT timezone FROM users WHERE id = ? LIMIT 1",
            arrayOf(userContext.requireCurrentUserId())
        ).use { cursor ->
            if (cursor.moveToFirst()) (cursor.getStringOrNull(0)?.takeIf { it.isNotBlank() }) ?: "Asia/Shanghai" else "Asia/Shanghai"
        }
    }

    private fun scalarLong(sql: String, vararg args: String): Long {
        return database.readableDb().rawQuery(sql, args).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) 0L else cursor.getLong(0)
        }
    }

    private fun upsertCurrentMonthBaseline(basicLivingCents: Long?, fixedSubscriptionCents: Long?) {
        val month = YearMonth.now().toString()
        val current = getMonthlyBaseline(month)
        upsertBaselineForMonth(
            month,
            basicLivingCents ?: current.basicLivingCents,
            fixedSubscriptionCents ?: current.fixedSubscriptionCents
        )
    }

    private fun structuralExpenseForWindow(userId: String, startDate: String, endDate: String, necessaryOnly: Boolean): Long {
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        if (end.isBefore(start)) return 0L
        
        var total = 0L
        var cursor = YearMonth.from(start)
        val target = YearMonth.from(end)
        
        while (!cursor.isAfter(target)) {
            val monthStart = cursor.atDay(1)
            val monthEnd = cursor.atEndOfMonth()
            val overlapStart = if (start.isAfter(monthStart)) start else monthStart
            val overlapEnd = if (end.isBefore(monthEnd)) end else monthEnd
            
            if (!overlapEnd.isBefore(overlapStart)) {
                val overlapDays = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1
                val monthDays = cursor.lengthOfMonth()
                
                val baselineMonthly = baselineMonthlyCost(userId, cursor.toString())
                val recurringMonthly = recurringMonthlyCost(userId, cursor.toString(), necessaryOnly)
                val capexMonthly = capexMonthlyCost(userId, cursor.toString(), necessaryOnly)
                val monthTotal = baselineMonthly + recurringMonthly + capexMonthly
                
                total += monthTotal * overlapDays / monthDays
            }
            cursor = cursor.plusMonths(1)
        }
        return total
    }

    private fun baselineMonthlyCost(userId: String, month: String): Long {
        return scalarLong("SELECT COALESCE(basic_living_cents, 0) FROM expense_baseline_month WHERE owner_user_id = ? AND month = ? LIMIT 1", userId, month)
    }

    private fun recurringMonthlyCost(userId: String, month: String, necessaryOnly: Boolean): Long {
        val sql = if (necessaryOnly) {
            "SELECT COALESCE(SUM(monthly_amount_cents), 0) FROM expense_recurring_rule WHERE owner_user_id = ? AND is_active = 1 AND is_necessary = 1 AND start_month <= ? AND (end_month IS NULL OR end_month = '' OR end_month >= ?)"
        } else {
            "SELECT COALESCE(SUM(monthly_amount_cents), 0) FROM expense_recurring_rule WHERE owner_user_id = ? AND is_active = 1 AND start_month <= ? AND (end_month IS NULL OR end_month = '' OR end_month >= ?)"
        }
        return scalarLong(sql, userId, month, month)
    }

    private fun capexMonthlyCost(userId: String, month: String, necessaryOnly: Boolean): Long {
        if (necessaryOnly) return 0L
        return scalarLong("SELECT COALESCE(SUM(monthly_amortized_cents), 0) FROM expense_capex WHERE owner_user_id = ? AND is_active = 1 AND amortization_start_month <= ? AND amortization_end_month >= ?", userId, month, month)
    }

    private fun upsertBaselineForMonth(month: String, basicLivingCents: Long, fixedSubscriptionCents: Long) {
        val userId = userContext.requireCurrentUserId()
        val now = Instant.now().toString()
        val values = ContentValues().apply {
            put("owner_user_id", userId)
            put("month", month)
            put("basic_living_cents", basicLivingCents.coerceAtLeast(0L))
            put("fixed_subscription_cents", fixedSubscriptionCents.coerceAtLeast(0L))
            put("updated_at", now)
            put("created_at", now)
        }
        database.writableDb().insertWithOnConflict("expense_baseline_month", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
