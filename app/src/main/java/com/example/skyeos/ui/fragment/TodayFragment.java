package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import com.example.skyeos.domain.usecase.LifeOsUseCases;


import com.example.skyeos.MainActivity;
import com.example.skyeos.R;
import com.example.skyeos.data.config.TimeGoalStore;
import com.example.skyeos.domain.model.CapexCostSummary;
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.MonthlyCostBaseline;
import com.example.skyeos.domain.model.RateComparisonSummary;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.RecurringCostRuleSummary;
import com.example.skyeos.domain.model.WindowOverview;
import com.example.skyeos.ui.util.UiFormatters;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Locale;

@AndroidEntryPoint
public class TodayFragment extends Fragment {

    @Inject
    CurrentUserContext userContext;

    @Inject
    LifeOsDatabase database;

    @Inject
    LifeOsUseCases useCases;

    @Inject
    com.example.skyeos.data.config.TimeGoalStore goalStore;

    
    
    private TextView tvGreeting, tvDate;
    private TextView tvDailyGoalStatus;
    private TextView tvMetricTime, tvMetricIncome, tvMetricExpense, tvMetricFreedom;
    private TextView tvTimeDistribution;
    private TextView tvHourlyDebtSummary;
    private TextView tvRateCompareSummary;
    private TextView tvCostFormulaSummary;
    private TextView tvTodayNotes;
    private TextView tvMetricsTitle;
    private TextView tvTimeTitle;
    private TextView tvNotesTitle;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_today, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvDate = view.findViewById(R.id.tv_date);
        tvDailyGoalStatus = view.findViewById(R.id.tv_daily_goal_status);
        tvMetricTime = view.findViewById(R.id.tv_metric_time);
        tvMetricIncome = view.findViewById(R.id.tv_metric_income);
        tvMetricExpense = view.findViewById(R.id.tv_metric_expense);
        tvMetricFreedom = view.findViewById(R.id.tv_metric_freedom);
        tvTimeDistribution = view.findViewById(R.id.tv_time_distribution);
        tvHourlyDebtSummary = view.findViewById(R.id.tv_hourly_debt_summary);
        tvRateCompareSummary = view.findViewById(R.id.tv_rate_compare_summary);
        tvCostFormulaSummary = view.findViewById(R.id.tv_cost_formula_summary);
        tvTodayNotes = view.findViewById(R.id.tv_today_notes);
        tvMetricsTitle = view.findViewById(R.id.tv_today_metrics_title);
        tvTimeTitle = view.findViewById(R.id.tv_today_time_title);
        tvNotesTitle = view.findViewById(R.id.tv_today_notes_title);

        View btnOpenSettings = view.findViewById(R.id.btn_open_settings);
        if (btnOpenSettings != null) {
            btnOpenSettings.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openSettings();
                }
            });
        }

        View btnOpenDayDetail = view.findViewById(R.id.btn_open_day_detail);
        if (btnOpenDayDetail != null) {
            btnOpenDayDetail.setOnClickListener(v -> openDayDetail());
        }

        MaterialButton btnCapture = view.findViewById(R.id.btn_quick_capture);
        btnCapture.setOnClickListener(v -> {
            // Navigate to Capture tab
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                activity.navigateTo(R.id.nav_capture);
            }
        });

        View btnDailyReview = view.findViewById(R.id.card_daily_review);
        if (btnDailyReview != null) {
            btnDailyReview.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateTo(R.id.nav_review);
                }
            });
        }

        setGreeting();
        refreshMetrics();
    }

    @Override
    public void onResume() {
        super.onResume();
        setGreeting();
        refreshMetrics();
    }

    private void setGreeting() {
        int hour = LocalTime.now().getHour();
        String greeting;
        if (hour < 12) {
            greeting = getString(R.string.today_greeting_morning);
        } else if (hour < 18) {
            greeting = getString(R.string.today_greeting_afternoon);
        } else {
            greeting = getString(R.string.today_greeting_evening);
        }
        tvGreeting.setText(greeting);

        LocalDate today = LocalDate.now();
        tvDate.setText(String.format(Locale.US, "%04d-%02d-%02d", today.getYear(), today.getMonthValue(),
                today.getDayOfMonth()));
        if (tvMetricsTitle != null) {
            tvMetricsTitle.setText(R.string.today_metrics_title);
        }
        if (tvTimeTitle != null) {
            tvTimeTitle.setText(R.string.today_time_title);
        }
        if (tvNotesTitle != null) {
            tvNotesTitle.setText(R.string.today_notes_title);
        }
    }

    private void refreshMetrics() {
        try {
            String day = LocalDate.now().toString();
            WindowOverview overview = useCases.getOverview.execute(day, "day");
            MetricSnapshotSummary snapshot = useCases.recomputeMetricSnapshot.execute(day, "day");

            // Time (minutes → hours)
            long workMinutes = overview.totalWorkMinutes;
            if (workMinutes > 0) {
                tvMetricTime.setText(String.format(Locale.US, "%dh %02dm", workMinutes / 60, workMinutes % 60));
            } else {
            tvMetricTime.setText("--");
            }

            // Income (cents → yuan)
            tvMetricIncome.setText(UiFormatters.yuan(requireContext(), overview.totalIncomeCents));

            // Expense (cents → yuan)
            refreshExpenseLabel(overview);

            // Freedom
            if (snapshot.freedomCents != null) {
                tvMetricFreedom.setText(UiFormatters.yuan(requireContext(), snapshot.freedomCents));
            } else {
                tvMetricFreedom.setText("--");
            }

            // Time distribution
            buildTimeDistribution(overview);
            buildHourlyDebtSummary(snapshot);
            buildRateCompareSummary(day);
            buildCostFormulaSummary(day, overview);
            buildDailyGoalStatus(overview);
            buildTodayNotes(day);

        } catch (Exception e) {
            // silently handle in case of DB issues
        }
    }

    private void buildTimeDistribution(WindowOverview overview) {
        long learningMinutes = overview.totalLearningMinutes;
        long visibleTotalMinutes = overview.totalTimeMinutes + learningMinutes;
        if (visibleTotalMinutes <= 0) {
            tvTimeDistribution.setText(R.string.today_no_recorded_time);
            return;
        }

        StringBuilder sb = new StringBuilder();
        double publicRatio = overview.publicTimeRatio * 100;
        double projectRatio = (1 - overview.publicTimeRatio) * 100;
        long totalH = visibleTotalMinutes / 60;
        long totalM = visibleTotalMinutes % 60;
        sb.append(getString(R.string.today_total_time_format, totalH, totalM)).append('\n');

        if (projectRatio > 0) {
            sb.append(getString(R.string.today_project_ratio_format, projectRatio)).append('\n');
        }
        if (publicRatio > 0) {
            sb.append(getString(R.string.today_public_pool_ratio_format, publicRatio)).append('\n');
        }
        if (learningMinutes > 0) {
            sb.append(getString(R.string.today_learning_time_format, learningMinutes / 60, learningMinutes % 60));
        }

        tvTimeDistribution.setText(sb.toString().trim());
    }

    private void buildTodayNotes(String today) {
        if (tvTodayNotes == null) {
            return;
        }
        List<RecentRecordItem> records = useCases.getRecordsForDate.execute(today, 80);
        if (records == null || records.isEmpty()) {
            tvTodayNotes.setText(R.string.today_no_notes);
            return;
        }
        List<String> notes = new ArrayList<>();
        for (RecentRecordItem item : records) {
            if (item == null) {
                continue;
            }
            String detail = item.detail == null ? "" : item.detail.trim();
            if (detail.isEmpty()) {
                continue;
            }
            String note = extractNote(detail);
            if (note.isEmpty()) {
                continue;
            }
            String prefix = iconFor(item.type);
            notes.add(prefix + " " + note);
            if (notes.size() >= 3) {
                break;
            }
        }
        if (notes.isEmpty()) {
            tvTodayNotes.setText(R.string.today_no_notes);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String note : notes) {
            sb.append("• ").append(note).append('\n');
        }
        tvTodayNotes.setText(sb.toString().trim());
    }

    private static String extractNote(String detail) {
        int idx = detail.indexOf('|');
        String candidate = idx >= 0 ? detail.substring(idx + 1).trim() : detail.trim();
        if (candidate.matches("^[0-9]+\\s*(cents|min)?$")) {
            return "";
        }
        return candidate;
    }

    private static String iconFor(String type) {
        if (type == null) {
            return "📝";
        }
        String t = type.toLowerCase(Locale.US);
        if (t.contains("expense")) {
            return "🧾";
        }
        if (t.contains("income")) {
            return "💰";
        }
        if (t.contains("learning")) {
            return "📚";
        }
        if (t.contains("time")) {
            return "⏱";
        }
        return "📝";
    }

    private void buildHourlyDebtSummary(MetricSnapshotSummary snapshot) {
        long ideal = useCases.getIdealHourlyRate.execute();
        if (snapshot == null || snapshot.hourlyRateCents == null || snapshot.timeDebtCents == null) {
            tvHourlyDebtSummary.setText(R.string.today_no_hourly_data);
            return;
        }
        String debtText;
        if (snapshot.timeDebtCents > 0) {
            debtText = getString(R.string.today_debt_format, UiFormatters.yuan(requireContext(), snapshot.timeDebtCents));
        } else if (snapshot.timeDebtCents < 0) {
            debtText = getString(R.string.today_surplus_format, UiFormatters.yuan(requireContext(), Math.abs(snapshot.timeDebtCents)));
        } else {
            debtText = getString(R.string.today_balanced);
        }
        tvHourlyDebtSummary.setText(getString(R.string.today_hourly_summary_format,
                UiFormatters.yuan(requireContext(), snapshot.hourlyRateCents), UiFormatters.yuan(requireContext(), ideal), debtText));
    }

    private void buildRateCompareSummary(String today) {
        if (tvRateCompareSummary == null) {
            return;
        }
        RateComparisonSummary rates = useCases.getRateComparison.execute(today, "month");
        String monthLabel = today.length() >= 7 ? today.substring(0, 7) : today;
        tvRateCompareSummary.setText(getString(
                R.string.today_rate_compare_format,
                monthLabel,
                UiFormatters.yuan(requireContext(), rates.idealHourlyRateCents),
                formatNullableHourly(rates.previousYearAverageHourlyRateCents),
                formatNullableHourly(rates.actualHourlyRateCents)));
    }

    private void buildCostFormulaSummary(String day, WindowOverview overview) {
        if (tvCostFormulaSummary == null) {
            return;
        }
        try {
            String month = day != null && day.length() >= 7 ? day.substring(0, 7) : YearMonth.now().toString();
            MonthlyCostBaseline baseline = useCases.getMonthlyCostBaseline.execute(month);
            List<RecurringCostRuleSummary> recurringRules = useCases.listRecurringCostRules.execute();
            List<CapexCostSummary> capexCosts = useCases.listCapexCosts.execute();
            long recurringCents = recurringMonthlyForMonth(recurringRules, month);
            long capexCents = capexMonthlyForMonth(capexCosts, month);
            long monthlyTotal = Math.max(0L, baseline.basicLivingCents) + recurringCents + capexCents;
            int monthDays = YearMonth.parse(month).lengthOfMonth();
            long dailyTotal = monthDays <= 0 ? 0L : Math.round(monthlyTotal / (double) monthDays);
            long actualExpense = overview == null ? 0L : Math.max(0L, overview.actualExpenseCents);
            long structuralExpense = overview == null ? 0L : Math.max(0L, overview.structuralExpenseCents);
            long totalExpense = overview == null ? 0L : Math.max(0L, overview.totalExpenseCents);
            tvCostFormulaSummary.setText(getString(
                    R.string.today_cost_formula_format,
                    month,
                    UiFormatters.yuan(requireContext(), Math.max(0L, baseline.basicLivingCents)),
                    UiFormatters.yuan(requireContext(), recurringCents),
                    UiFormatters.yuan(requireContext(), capexCents),
                    UiFormatters.yuan(requireContext(), monthlyTotal),
                    UiFormatters.yuan(requireContext(), dailyTotal),
                    UiFormatters.yuan(requireContext(), actualExpense),
                    UiFormatters.yuan(requireContext(), structuralExpense),
                    UiFormatters.yuan(requireContext(), totalExpense)));
        } catch (Exception e) {
            tvCostFormulaSummary.setText(R.string.today_cost_formula_empty);
        }
    }

    private static long recurringMonthlyForMonth(List<RecurringCostRuleSummary> recurringRules, String month) {
        if (recurringRules == null || recurringRules.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (RecurringCostRuleSummary item : recurringRules) {
            if (item != null && isRecurringActiveInMonth(item, month)) {
                total += Math.max(0L, item.monthlyAmountCents);
            }
        }
        return total;
    }

    private static long capexMonthlyForMonth(List<CapexCostSummary> capexCosts, String month) {
        if (capexCosts == null || capexCosts.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (CapexCostSummary item : capexCosts) {
            if (item != null && isCapexActiveInMonth(item, month)) {
                total += Math.max(0L, item.monthlyAmortizedCents);
            }
        }
        return total;
    }

    private static boolean isRecurringActiveInMonth(RecurringCostRuleSummary item, String month) {
        if (item == null || !item.active) {
            return false;
        }
        String target = normalizeMonthOrFallback(month);
        String start = normalizeMonthOrFallback(item.startMonth);
        String end = item.endMonth == null || item.endMonth.trim().isEmpty()
                ? null
                : normalizeMonthOrFallback(item.endMonth);
        if (target.compareTo(start) < 0) {
            return false;
        }
        return end == null || target.compareTo(end) <= 0;
    }

    private static boolean isCapexActiveInMonth(CapexCostSummary item, String month) {
        if (item == null || !item.active) {
            return false;
        }
        String target = normalizeMonthOrFallback(month);
        String start = normalizeMonthOrFallback(item.amortizationStartMonth);
        String end = normalizeMonthOrFallback(item.amortizationEndMonth);
        return target.compareTo(start) >= 0 && target.compareTo(end) <= 0;
    }

    private static String normalizeMonthOrFallback(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) {
                return YearMonth.now().toString();
            }
            return YearMonth.parse(raw.trim()).toString();
        } catch (Exception e) {
            return YearMonth.now().toString();
        }
    }

    private String formatNullableHourly(Long cents) {
        return UiFormatters.nullableHourly(requireContext(), cents);
    }

    private void buildDailyGoalStatus(WindowOverview overview) {
        if (tvDailyGoalStatus == null) {
            return;
        }
        TimeGoalStore.Goal goal = goalStore.load();
        if (!goal.isConfigured()) {
            tvDailyGoalStatus.setText(R.string.today_goal_status_unset);
            tvDailyGoalStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
            return;
        }
        int workMinutes = toIntMinutes(overview == null ? 0L : overview.totalWorkMinutes);
        int learningMinutes = toIntMinutes(overview == null ? 0L : overview.totalLearningMinutes);
        boolean reached = goal.isReached(workMinutes, learningMinutes);
        int colorRes = reached ? R.color.statusPositive : R.color.statusNegative;
        tvDailyGoalStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (reached) {
            tvDailyGoalStatus.setText(getString(
                    R.string.today_goal_status_reached,
                    workMinutes, goal.minWorkMinutes,
                    learningMinutes, goal.minLearningMinutes));
        } else {
            tvDailyGoalStatus.setText(getString(
                    R.string.today_goal_status_unreached,
                    workMinutes, goal.minWorkMinutes,
                    learningMinutes, goal.minLearningMinutes));
        }
    }

    private static int toIntMinutes(long minutes) {
        if (minutes <= 0L) {
            return 0;
        }
        if (minutes > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) minutes;
    }

    private void refreshExpenseLabel(WindowOverview overview) {
        if (tvMetricExpense == null) {
            return;
        }
        if (overview.structuralExpenseCents > 0) {
            tvMetricExpense.setText(UiFormatters.yuan(requireContext(), overview.totalExpenseCents));
        } else {
            tvMetricExpense.setText(UiFormatters.yuan(requireContext(), overview.actualExpenseCents));
        }
    }

    private void openDayDetail() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openDayDetail(LocalDate.now().toString());
        }
    }
}
