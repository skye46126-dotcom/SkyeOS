package com.example.skyeos.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.skyeos.AppGraph;
import com.example.skyeos.MainActivity;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.RateComparisonSummary;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.WindowOverview;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

public class TodayFragment extends Fragment {

    private AppGraph graph;
    private TextView tvGreeting, tvDate;
    private TextView tvMetricTime, tvMetricIncome, tvMetricExpense, tvMetricFreedom;
    private TextView tvTimeDistribution;
    private TextView tvHourlyDebtSummary;
    private TextView tvRateCompareSummary;
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
        graph = AppGraph.getInstance(requireContext());

        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvDate = view.findViewById(R.id.tv_date);
        tvMetricTime = view.findViewById(R.id.tv_metric_time);
        tvMetricIncome = view.findViewById(R.id.tv_metric_income);
        tvMetricExpense = view.findViewById(R.id.tv_metric_expense);
        tvMetricFreedom = view.findViewById(R.id.tv_metric_freedom);
        tvTimeDistribution = view.findViewById(R.id.tv_time_distribution);
        tvHourlyDebtSummary = view.findViewById(R.id.tv_hourly_debt_summary);
        tvRateCompareSummary = view.findViewById(R.id.tv_rate_compare_summary);
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

        View costManagementCard = view.findViewById(R.id.card_cost_management);
        if (costManagementCard != null) {
            costManagementCard.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openCostManagement();
                }
            });
        }

        View btnDailyReview = view.findViewById(R.id.card_daily_review);
        if (btnDailyReview != null) {
            btnDailyReview.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openDayDetail(LocalDate.now().toString());
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
            WindowOverview overview = graph.useCases.getOverview.execute(day, "day");
            MetricSnapshotSummary snapshot = graph.useCases.recomputeMetricSnapshot.execute(day, "day");

            // Time (minutes → hours)
            long workMinutes = overview.totalWorkMinutes;
            if (workMinutes > 0) {
                tvMetricTime.setText(String.format(Locale.US, "%dh %02dm", workMinutes / 60, workMinutes % 60));
            } else {
            tvMetricTime.setText("--");
            }

            // Income (cents → yuan)
            tvMetricIncome.setText(formatYuan(overview.totalIncomeCents));

            // Expense (cents → yuan)
            refreshExpenseLabel(overview);

            // Freedom
            if (snapshot.freedomCents != null) {
                tvMetricFreedom.setText(formatYuan(snapshot.freedomCents));
            } else {
                tvMetricFreedom.setText("--");
            }

            // Time distribution
            buildTimeDistribution(overview);
            buildHourlyDebtSummary(snapshot);
            buildRateCompareSummary(day);
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
        List<RecentRecordItem> records = graph.useCases.getRecordsForDate.execute(today, 80);
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
        long ideal = graph.useCases.getIdealHourlyRate.execute();
        if (snapshot == null || snapshot.hourlyRateCents == null || snapshot.timeDebtCents == null) {
            tvHourlyDebtSummary.setText(R.string.today_no_hourly_data);
            return;
        }
        String debtText;
        if (snapshot.timeDebtCents > 0) {
            debtText = getString(R.string.today_debt_format, formatYuan(snapshot.timeDebtCents));
        } else if (snapshot.timeDebtCents < 0) {
            debtText = getString(R.string.today_surplus_format, formatYuan(Math.abs(snapshot.timeDebtCents)));
        } else {
            debtText = getString(R.string.today_balanced);
        }
        tvHourlyDebtSummary.setText(getString(R.string.today_hourly_summary_format,
                formatYuan(snapshot.hourlyRateCents), formatYuan(ideal), debtText));
    }

    private void buildRateCompareSummary(String today) {
        if (tvRateCompareSummary == null) {
            return;
        }
        RateComparisonSummary rates = graph.useCases.getRateComparison.execute(today, "month");
        String monthLabel = today.length() >= 7 ? today.substring(0, 7) : today;
        tvRateCompareSummary.setText(getString(
                R.string.today_rate_compare_format,
                monthLabel,
                formatYuan(rates.idealHourlyRateCents),
                formatNullableHourly(rates.previousYearAverageHourlyRateCents),
                formatNullableHourly(rates.actualHourlyRateCents)));
    }

    private static String formatNullableHourly(Long cents) {
        return cents == null ? "--" : formatYuan(cents) + "/h";
    }

    private static String formatYuan(long cents) {
        if (cents == 0)
            return "--";
        if (cents % 100 == 0) {
            return String.format(Locale.US, "¥%,d", cents / 100);
        }
        return String.format(Locale.US, "¥%.2f", cents / 100.0);
    }

    private void refreshExpenseLabel(WindowOverview overview) {
        if (tvMetricExpense == null) {
            return;
        }
        if (overview.structuralExpenseCents > 0) {
            tvMetricExpense.setText(formatYuan(overview.totalExpenseCents));
        } else {
            tvMetricExpense.setText(formatYuan(overview.actualExpenseCents));
        }
    }

    private void openDayDetail() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openDayDetail(LocalDate.now().toString());
        }
    }
}
