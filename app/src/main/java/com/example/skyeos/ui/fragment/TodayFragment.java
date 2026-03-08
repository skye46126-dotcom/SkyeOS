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
import com.example.skyeos.R;
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.WindowOverview;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

public class TodayFragment extends Fragment {

    private AppGraph graph;
    private TextView tvGreeting, tvDate;
    private TextView tvMetricTime, tvMetricIncome, tvMetricExpense, tvMetricFreedom;
    private TextView tvTimeDistribution;
    private TextView tvHourlyDebtSummary;

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

        MaterialButton btnCapture = view.findViewById(R.id.btn_quick_capture);
        btnCapture.setOnClickListener(v -> {
            // Navigate to Capture tab
            if (getActivity() instanceof com.example.skyeos.MainActivity) {
                com.example.skyeos.MainActivity activity = (com.example.skyeos.MainActivity) getActivity();
                activity.navigateTo(R.id.nav_capture);
            }
        });

        View btnDailyReview = view.findViewById(R.id.card_daily_review);
        if (btnDailyReview != null) {
            btnDailyReview.setOnClickListener(v -> {
                // For now, navigate to Review tab. In actual implementation, we'd open a dialog
                // or sub-screen.
                if (getActivity() instanceof com.example.skyeos.MainActivity) {
                    ((com.example.skyeos.MainActivity) getActivity()).navigateTo(R.id.nav_review);
                }
            });
        }

        setGreeting();
        refreshMetrics();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshMetrics();
    }

    private void setGreeting() {
        int hour = LocalTime.now().getHour();
        String greeting;
        if (hour < 12)
            greeting = "Good morning ☀️";
        else if (hour < 18)
            greeting = "Good afternoon 🌤";
        else
            greeting = "Good evening 🌙";
        tvGreeting.setText(greeting);

        LocalDate today = LocalDate.now();
        tvDate.setText(String.format(Locale.US, "%04d-%02d-%02d", today.getYear(), today.getMonthValue(),
                today.getDayOfMonth()));
    }

    private void refreshMetrics() {
        try {
            String today = LocalDate.now().toString();
            WindowOverview overview = graph.useCases.getOverview.execute(today, "day");
            MetricSnapshotSummary snapshot = graph.useCases.recomputeMetricSnapshot.execute(today, "day");

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
            tvMetricExpense.setText(formatYuan(overview.totalExpenseCents));

            // Freedom
            if (snapshot.freedomCents != null) {
                tvMetricFreedom.setText(formatYuan(snapshot.freedomCents));
            } else {
                tvMetricFreedom.setText("--");
            }

            // Time distribution
            buildTimeDistribution(overview);
            buildHourlyDebtSummary(snapshot);

        } catch (Exception e) {
            // silently handle in case of DB issues
        }
    }

    private void buildTimeDistribution(WindowOverview overview) {
        if (overview.totalTimeMinutes <= 0) {
            tvTimeDistribution.setText("No time logs today");
            return;
        }

        StringBuilder sb = new StringBuilder();
        double publicRatio = overview.publicTimeRatio * 100;
        double projectRatio = (1 - overview.publicTimeRatio) * 100;

        long totalH = overview.totalTimeMinutes / 60;
        long totalM = overview.totalTimeMinutes % 60;
        sb.append(String.format(Locale.US, "Total %dh %02dm\n", totalH, totalM));

        if (projectRatio > 0) {
            sb.append(String.format(Locale.US, "📌 Project  %.0f%%\n", projectRatio));
        }
        if (publicRatio > 0) {
            sb.append(String.format(Locale.US, "🔵 Public pool  %.0f%%", publicRatio));
        }

        tvTimeDistribution.setText(sb.toString().trim());
    }

    private void buildHourlyDebtSummary(MetricSnapshotSummary snapshot) {
        long ideal = graph.useCases.getIdealHourlyRate.execute();
        if (snapshot == null || snapshot.hourlyRateCents == null || snapshot.timeDebtCents == null) {
            tvHourlyDebtSummary.setText("No hourly rate / time debt data yet");
            return;
        }
        String debtText;
        if (snapshot.timeDebtCents > 0) {
            debtText = "Debt " + formatYuan(snapshot.timeDebtCents) + "/h";
        } else if (snapshot.timeDebtCents < 0) {
            debtText = "Surplus " + formatYuan(Math.abs(snapshot.timeDebtCents)) + "/h";
        } else {
            debtText = "Balanced";
        }
        tvHourlyDebtSummary.setText(String.format(Locale.US, "Actual %s/h | Ideal %s/h | %s",
                formatYuan(snapshot.hourlyRateCents), formatYuan(ideal), debtText));
    }

    private static String formatYuan(long cents) {
        if (cents == 0)
            return "--";
        if (cents % 100 == 0) {
            return String.format(Locale.US, "¥%,d", cents / 100);
        }
        return String.format(Locale.US, "¥%.2f", cents / 100.0);
    }
}
