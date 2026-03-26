package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import com.example.skyeos.domain.usecase.LifeOsUseCases;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.example.skyeos.MainActivity;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.ReviewReport;
import com.example.skyeos.domain.usecase.LifeOsUseCases;
import com.example.skyeos.ui.util.UiFormatters;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AndroidEntryPoint
public class ReviewFragment extends Fragment {

    @Inject
    CurrentUserContext userContext;

    @Inject
    LifeOsDatabase database;
    private static final String TAG = "ReviewFragment";

    @Inject
    LifeOsUseCases useCases;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TabLayout tabReviewPeriod;
    private TextView tvReviewTitle;
    private TextView tvReviewSubtitle;
    private TextView tvReviewAnchor;
    private TextView tvAiSummary;
    private TextView tvCostDebtSummary;
    private TextView tvTrendSummary;
    private TextView tvAiAssistMetric;
    private TextView tvWorkEffMetric;
    private TextView tvLearningEffMetric;
    private TextView tvTrendDetailTitle;

    private RecyclerView rvTimeAllocation;
    TimeAllocationAdapter timeAdapter;

    private RecyclerView rvTopProjects;
    private ReviewProjectAdapter topProjectAdapter;

    private RecyclerView rvSinkholeProjects;
    private ReviewProjectAdapter sinkholeAdapter;

    private RecyclerView rvKeyEvents;
    RecentRecordsAdapter keyEventsAdapter;
    private TextView tvIncomeHistorySummary;
    private RecyclerView rvIncomeHistory;
    RecentRecordsAdapter incomeHistoryAdapter;
    private ChipGroup chipGroupTrendDetail;
    private RecyclerView rvTrendDetailRecords;
    RecentRecordsAdapter trendDetailAdapter;
    private ChipGroup chipGroupHistoryFilter;
    private TextView tvHistoryLedgerSummary;
    private RecyclerView rvHistoryLedger;
    RecentRecordsAdapter historyLedgerAdapter;
    private RecyclerView rvTagTimeMetrics;
    private RecyclerView rvTagExpenseMetrics;
    private RecyclerView rvTagDetailRecords;
    private TextView tvTagDetailTitle;
    TagMetricAdapter timeTagAdapter;
    TagMetricAdapter expenseTagAdapter;
    RecentRecordsAdapter tagDetailAdapter;
    private List<com.example.skyeos.domain.model.RecentRecordItem> latestHistoryRecords = new ArrayList<>();
    private int currentTabPosition = 0;
    private ReviewReport latestReport;

    private LocalDate currentDate = LocalDate.now();
    private boolean customRangeEnabled = false;
    private LocalDate customStartDate = LocalDate.now().minusDays(6);
    private LocalDate customEndDate = LocalDate.now();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_review, container, false);


        tvReviewTitle = view.findViewById(R.id.tv_review_title);
        tvReviewSubtitle = view.findViewById(R.id.tv_review_subtitle);
        tvReviewAnchor = view.findViewById(R.id.tv_review_anchor);
        tvAiSummary = view.findViewById(R.id.tv_ai_summary);
        tvCostDebtSummary = view.findViewById(R.id.tv_cost_debt_summary);
        tvTrendSummary = view.findViewById(R.id.tv_trend_summary);
        tvAiAssistMetric = view.findViewById(R.id.tv_ai_assist_metric);
        tvWorkEffMetric = view.findViewById(R.id.tv_work_eff_metric);
        tvLearningEffMetric = view.findViewById(R.id.tv_learning_eff_metric);
        tvTrendDetailTitle = view.findViewById(R.id.tv_trend_detail_title);

        tabReviewPeriod = view.findViewById(R.id.tab_review_period);
        MaterialButton btnReviewPrev = view.findViewById(R.id.btn_review_prev);
        MaterialButton btnReviewNext = view.findViewById(R.id.btn_review_next);
        MaterialButton btnReviewToday = view.findViewById(R.id.btn_review_today);
        MaterialButton btnReviewOpenDayDetail = view.findViewById(R.id.btn_review_open_day_detail);
        MaterialButton btnReviewOpenAiChat = view.findViewById(R.id.btn_review_open_ai_chat);
        tabReviewPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTabPosition = tab.getPosition();
                updateAnchorUi();
                loadReportForTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                currentTabPosition = tab.getPosition();
                updateAnchorUi();
                loadReportForTab(tab.getPosition());
            }
        });
        btnReviewPrev.setOnClickListener(v -> shiftAnchor(-1));
        btnReviewNext.setOnClickListener(v -> shiftAnchor(1));
        btnReviewToday.setOnClickListener(v -> {
            currentDate = LocalDate.now();
            customRangeEnabled = false;
            updateAnchorUi();
            loadReportForCurrentTab();
        });
        btnReviewOpenDayDetail.setOnClickListener(v -> openAnchorDayDetail());
        btnReviewOpenAiChat.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openAiChat();
            }
        });
        tvReviewAnchor.setOnClickListener(v -> {
            if (customRangeEnabled) {
                openCustomRangePicker();
            } else {
                openAnchorDatePicker();
            }
        });
        tvReviewAnchor.setOnLongClickListener(v -> {
            openCustomRangePicker();
            return true;
        });

        // Setup RecyclerViews
        rvTimeAllocation = view.findViewById(R.id.rv_time_allocation);
        rvTimeAllocation.setLayoutManager(new LinearLayoutManager(requireContext()));

        timeAdapter = new TimeAllocationAdapter();
        rvTimeAllocation.setAdapter(timeAdapter);

        rvTopProjects = view.findViewById(R.id.rv_top_projects);
        rvTopProjects.setLayoutManager(new LinearLayoutManager(requireContext()));

        topProjectAdapter = new ReviewProjectAdapter();
        rvTopProjects.setAdapter(topProjectAdapter);

        rvSinkholeProjects = view.findViewById(R.id.rv_sinkhole_projects);
        rvSinkholeProjects.setLayoutManager(new LinearLayoutManager(requireContext()));

        sinkholeAdapter = new ReviewProjectAdapter();
        rvSinkholeProjects.setAdapter(sinkholeAdapter);

        rvKeyEvents = view.findViewById(R.id.rv_key_events);
        rvKeyEvents.setLayoutManager(new LinearLayoutManager(requireContext()));

        keyEventsAdapter = new RecentRecordsAdapter();
        rvKeyEvents.setAdapter(keyEventsAdapter);

        tvIncomeHistorySummary = view.findViewById(R.id.tv_income_history_summary);
        rvIncomeHistory = view.findViewById(R.id.rv_income_history);
        rvIncomeHistory.setLayoutManager(new LinearLayoutManager(requireContext()));

        incomeHistoryAdapter = new RecentRecordsAdapter();
        rvIncomeHistory.setAdapter(incomeHistoryAdapter);
        chipGroupTrendDetail = view.findViewById(R.id.chip_group_trend_detail);
        rvTrendDetailRecords = view.findViewById(R.id.rv_trend_detail_records);
        rvTrendDetailRecords.setLayoutManager(new LinearLayoutManager(requireContext()));

        trendDetailAdapter = new RecentRecordsAdapter();
        rvTrendDetailRecords.setAdapter(trendDetailAdapter);
        chipGroupTrendDetail.setOnCheckedStateChangeListener((group, checkedIds) -> applyTrendDetailSelection());

        chipGroupHistoryFilter = view.findViewById(R.id.chip_group_history_filter);
        tvHistoryLedgerSummary = view.findViewById(R.id.tv_history_ledger_summary);
        rvHistoryLedger = view.findViewById(R.id.rv_history_ledger);
        rvHistoryLedger.setLayoutManager(new LinearLayoutManager(requireContext()));

        rvHistoryLedger.setAdapter(historyLedgerAdapter);
        chipGroupHistoryFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyHistoryFilter());
        rvTagTimeMetrics = view.findViewById(R.id.rv_tag_time_metrics);
        rvTagTimeMetrics.setLayoutManager(new LinearLayoutManager(requireContext()));

        timeTagAdapter = new TagMetricAdapter(false, metric -> loadTagDetail("time", metric));
        rvTagTimeMetrics.setAdapter(timeTagAdapter);
        rvTagExpenseMetrics = view.findViewById(R.id.rv_tag_expense_metrics);
        rvTagExpenseMetrics.setLayoutManager(new LinearLayoutManager(requireContext()));

        expenseTagAdapter = new TagMetricAdapter(true, metric -> loadTagDetail("expense", metric));
        rvTagExpenseMetrics.setAdapter(expenseTagAdapter);
        rvTagDetailRecords = view.findViewById(R.id.rv_tag_detail_records);
        rvTagDetailRecords.setLayoutManager(new LinearLayoutManager(requireContext()));

        tagDetailAdapter = new RecentRecordsAdapter();
        rvTagDetailRecords.setAdapter(tagDetailAdapter);
        tvTagDetailTitle = view.findViewById(R.id.tv_tag_detail_title);

        // Default to daily so users immediately see today's window.
        TabLayout.Tab dayTab = tabReviewPeriod.getTabAt(0);
        if (dayTab != null) {
            tabReviewPeriod.selectTab(dayTab);
            currentTabPosition = 0;
        }
        updateAnchorUi();
        loadReportForCurrentTab();

        return view;
    }

    private void loadReportForTab(int tabPosition) {
        executor.execute(() -> {
            ReviewReport report = null;
            String errorMessage = null;
            try {
                if (customRangeEnabled) {
                    report = useCases.reviewUseCases.getRangeReview(customStartDate, customEndDate);
                } else if (tabPosition == 0) {
                    report = useCases.reviewUseCases.getDailyReview(currentDate);
                } else if (tabPosition == 1) {
                    report = useCases.reviewUseCases.getWeeklyReview(currentDate);
                } else if (tabPosition == 2) {
                    report = useCases.reviewUseCases.getMonthlyReview(currentDate);
                } else if (tabPosition == 3) {
                    report = useCases.reviewUseCases.getYearlyReview(currentDate);
                }
            } catch (Exception e) {
                Log.e(TAG, "loadReportForTab failed", e);
                errorMessage = e.getMessage();
            }

            final ReviewReport finalReport = report;
            final String finalErrorMessage = errorMessage;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (finalReport != null) {
                        displayReport(finalReport);
                    } else {
                        renderReportUnavailable(finalErrorMessage);
                    }
                });
            }
        });
    }

    private void displayReport(ReviewReport report) {
        latestReport = report;
        tvReviewTitle.setText(buildUiPeriodTitle(currentTabPosition, report.periodName));
        tvReviewSubtitle.setText(buildUiPeriodSubtitle(currentTabPosition));
        tvAiSummary.setText(report.aiSummary);
        tvCostDebtSummary.setText(formatCostDebtSummary(report));
        tvTrendSummary.setText(formatTrendSummary(report));
        tvAiAssistMetric.setText(getString(R.string.review_ai_assist_metric, formatPercentageOrDash(report.aiAssistRate)));
        tvWorkEffMetric.setText(getString(R.string.review_work_eff_metric, formatScoreOrDash(report.workEfficiencyAvg)));
        tvLearningEffMetric.setText(getString(R.string.review_learning_eff_metric, formatScoreOrDash(report.learningEfficiencyAvg)));

        timeAdapter.submitList(report.timeAllocations);
        topProjectAdapter.submitList(report.topProjects);
        sinkholeAdapter.submitList(report.sinkholeProjects);

        // Hide sinkhole section if empty
        View sinkholeContainer = (View) rvSinkholeProjects.getParent(); // MaterialCardView
        View sinkholeTitle = (View) sinkholeContainer.getParent();

        // Using adapter list to show key events
        keyEventsAdapter.submitList(report.keyEvents);

        List<com.example.skyeos.domain.model.RecentRecordItem> incomeItems = report.incomeHistory;
        incomeHistoryAdapter.submitList(incomeItems);
        tvIncomeHistorySummary.setText(buildIncomeSummary(incomeItems, report.totalIncomeCents));

        latestHistoryRecords = report.historyRecords == null ? new ArrayList<>() : report.historyRecords;
        applyHistoryFilter();
        timeTagAdapter.submitList(report.timeTagMetrics);
        expenseTagAdapter.submitList(report.expenseTagMetrics);
        autoLoadInitialTagDetail(report);
        applyTrendDetailSelection();
    }

    private void renderReportUnavailable(String errorMessage) {
        latestReport = null;
        tvReviewTitle.setText(buildUiPeriodTitle(currentTabPosition, null));
        tvReviewSubtitle.setText(buildUiPeriodSubtitle(currentTabPosition));
        String fallback = getString(R.string.review_load_failed);
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            fallback = getString(R.string.review_load_failed_reason, errorMessage);
        }
        tvAiSummary.setText(fallback);
        tvCostDebtSummary.setText(R.string.review_empty_state_hint);
        tvTrendSummary.setText(R.string.review_empty_state_hint);
        tvAiAssistMetric.setText(getString(R.string.review_ai_assist_metric, "--"));
        tvWorkEffMetric.setText(getString(R.string.review_work_eff_metric, "--"));
        tvLearningEffMetric.setText(getString(R.string.review_learning_eff_metric, "--"));
        timeAdapter.submitList(new ArrayList<>());
        topProjectAdapter.submitList(new ArrayList<>());
        sinkholeAdapter.submitList(new ArrayList<>());
        keyEventsAdapter.submitList(new ArrayList<>());
        incomeHistoryAdapter.submitList(new ArrayList<>());
        tvIncomeHistorySummary.setText(buildIncomeSummary(new ArrayList<>(), 0L));
        latestHistoryRecords = new ArrayList<>();
        applyHistoryFilter();
        timeTagAdapter.submitList(new ArrayList<>());
        expenseTagAdapter.submitList(new ArrayList<>());
        tvTagDetailTitle.setText(R.string.review_tag_details_empty);
        tagDetailAdapter.submitList(new ArrayList<>());
        tvTrendDetailTitle.setText(R.string.review_trend_details_empty);
        trendDetailAdapter.submitList(new ArrayList<>());
    }

    private String buildIncomeSummary(
            List<com.example.skyeos.domain.model.RecentRecordItem> incomeItems,
            long totalCents
    ) {
        double yuan = totalCents / 100.0;
        int count = incomeItems == null ? 0 : incomeItems.size();
        return getString(R.string.review_income_summary_format, count, yuan);
    }

    private void applyHistoryFilter() {
        if (historyLedgerAdapter == null) {
            return;
        }
        String selectedType = selectedHistoryType();
        List<com.example.skyeos.domain.model.RecentRecordItem> filtered = filterByType(latestHistoryRecords, selectedType);
        historyLedgerAdapter.submitList(filtered);
        if (tvHistoryLedgerSummary != null) {
            tvHistoryLedgerSummary.setText(getString(R.string.review_history_summary_format, capitalizeType(selectedType), filtered.size()));
        }
    }

    private String selectedHistoryType() {
        if (chipGroupHistoryFilter == null) {
            return "all";
        }
        int checkedId = chipGroupHistoryFilter.getCheckedChipId();
        if (checkedId == R.id.chip_history_income) {
            return "income";
        }
        if (checkedId == R.id.chip_history_expense) {
            return "expense";
        }
        if (checkedId == R.id.chip_history_time) {
            return "time";
        }
        if (checkedId == R.id.chip_history_learning) {
            return "learning";
        }
        return "all";
    }

    private static List<com.example.skyeos.domain.model.RecentRecordItem> filterByType(
            List<com.example.skyeos.domain.model.RecentRecordItem> source,
            String type
    ) {
        List<com.example.skyeos.domain.model.RecentRecordItem> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        if ("all".equals(type)) {
            out.addAll(source);
            return out;
        }
        for (com.example.skyeos.domain.model.RecentRecordItem item : source) {
            if (item == null || item.type == null) {
                continue;
            }
            if (type.equals(item.type)) {
                out.add(item);
            }
        }
        return out;
    }

    private void autoLoadInitialTagDetail(ReviewReport report) {
        if (report != null && report.timeTagMetrics != null && !report.timeTagMetrics.isEmpty()) {
            loadTagDetail("time", report.timeTagMetrics.get(0));
            return;
        }
        if (report != null && report.expenseTagMetrics != null && !report.expenseTagMetrics.isEmpty()) {
            loadTagDetail("expense", report.expenseTagMetrics.get(0));
            return;
        }
        if (tvTagDetailTitle != null) {
            tvTagDetailTitle.setText(R.string.review_tag_details_empty);
        }
        if (tagDetailAdapter != null) {
            tagDetailAdapter.submitList(new ArrayList<>());
        }
    }

    private void loadTagDetail(String scope, ReviewReport.TagMetric metric) {
        if (metric == null) {
            return;
        }
        if (tvTagDetailTitle != null) {
            tvTagDetailTitle.setText(getString(R.string.review_tag_details_format, metric.tagName, scope));
        }
        executor.execute(() -> {
            List<com.example.skyeos.domain.model.RecentRecordItem> rows;
            if (customRangeEnabled) {
                rows = useCases.reviewUseCases.getRangeTagDetail(customStartDate, customEndDate, scope, metric.tagName, 20);
            } else if (currentTabPosition == 0) {
                rows = useCases.reviewUseCases.getDailyTagDetail(currentDate, scope, metric.tagName, 20);
            } else if (currentTabPosition == 1) {
                rows = useCases.reviewUseCases.getWeeklyTagDetail(currentDate, scope, metric.tagName, 20);
            } else if (currentTabPosition == 2) {
                rows = useCases.reviewUseCases.getMonthlyTagDetail(currentDate, scope, metric.tagName, 20);
            } else {
                rows = useCases.reviewUseCases.getYearlyTagDetail(currentDate, scope, metric.tagName, 20);
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> tagDetailAdapter.submitList(rows));
            }
        });
    }

    private void applyTrendDetailSelection() {
        String kind = selectedTrendKind();
        loadTrendDetail(kind);
    }

    private String selectedTrendKind() {
        if (chipGroupTrendDetail == null) {
            return "income";
        }
        int checkedId = chipGroupTrendDetail.getCheckedChipId();
        if (checkedId == R.id.chip_trend_expense) {
            return "expense";
        }
        if (checkedId == R.id.chip_trend_work) {
            return "work";
        }
        return "income";
    }

    private void loadTrendDetail(String kind) {
        ReviewReport current = latestReport;
        if (current == null) {
            if (tvTrendDetailTitle != null) {
                tvTrendDetailTitle.setText(R.string.review_trend_details_empty);
            }
            if (trendDetailAdapter != null) {
                trendDetailAdapter.submitList(new ArrayList<>());
            }
            return;
        }
        executor.execute(() -> {
            ReviewReport previous = loadPreviousReport();
            List<com.example.skyeos.domain.model.RecentRecordItem> currentRows = pickTrendRows(current, kind);
            List<com.example.skyeos.domain.model.RecentRecordItem> previousRows = previous == null
                    ? new ArrayList<>()
                    : pickTrendRows(previous, kind);
            List<com.example.skyeos.domain.model.RecentRecordItem> merged = mergeTrendRows(currentRows, previousRows);
            String title = buildTrendDetailTitle(kind, current, previous);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    tvTrendDetailTitle.setText(title);
                    trendDetailAdapter.submitList(merged);
                });
            }
        });
    }

    private ReviewReport loadPreviousReport() {
        try {
            if (customRangeEnabled) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(customStartDate, customEndDate) + 1;
                return useCases.reviewUseCases.getRangeReview(customStartDate.minusDays(days), customEndDate.minusDays(days));
            }
            if (currentTabPosition == 0) {
                return useCases.reviewUseCases.getDailyReview(currentDate.minusDays(1));
            }
            if (currentTabPosition == 1) {
                return useCases.reviewUseCases.getWeeklyReview(currentDate.minusWeeks(1));
            }
            if (currentTabPosition == 2) {
                return useCases.reviewUseCases.getMonthlyReview(currentDate.minusMonths(1));
            }
            return useCases.reviewUseCases.getYearlyReview(currentDate.minusYears(1));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<com.example.skyeos.domain.model.RecentRecordItem> pickTrendRows(ReviewReport report, String kind) {
        List<com.example.skyeos.domain.model.RecentRecordItem> out = new ArrayList<>();
        if (report == null) {
            return out;
        }
        if ("income".equals(kind)) {
            List<com.example.skyeos.domain.model.RecentRecordItem> src = report.incomeHistory == null ? new ArrayList<>()
                    : report.incomeHistory;
            for (int i = 0; i < src.size() && i < 10; i++) {
                out.add(src.get(i));
            }
            return out;
        }
        List<com.example.skyeos.domain.model.RecentRecordItem> src = report.historyRecords == null ? new ArrayList<>()
                : report.historyRecords;
        for (com.example.skyeos.domain.model.RecentRecordItem item : src) {
            if (item == null || item.type == null) {
                continue;
            }
            if ("expense".equals(kind) && "expense".equals(item.type)) {
                out.add(item);
            } else if ("work".equals(kind) && "time".equals(item.type)
                    && item.title != null && "work".equalsIgnoreCase(item.title.trim())) {
                out.add(item);
            }
            if (out.size() >= 10) {
                break;
            }
        }
        return out;
    }

    private static List<com.example.skyeos.domain.model.RecentRecordItem> mergeTrendRows(
            List<com.example.skyeos.domain.model.RecentRecordItem> currentRows,
            List<com.example.skyeos.domain.model.RecentRecordItem> previousRows) {
        List<com.example.skyeos.domain.model.RecentRecordItem> merged = new ArrayList<>();
        merged.add(new com.example.skyeos.domain.model.RecentRecordItem("meta", "", "Current window", "Recent records"));
        if (currentRows == null || currentRows.isEmpty()) {
            merged.add(new com.example.skyeos.domain.model.RecentRecordItem("meta", "", "No records", ""));
        } else {
            merged.addAll(currentRows);
        }
        merged.add(new com.example.skyeos.domain.model.RecentRecordItem("meta", "", "Previous window", "Recent records"));
        if (previousRows == null || previousRows.isEmpty()) {
            merged.add(new com.example.skyeos.domain.model.RecentRecordItem("meta", "", "No records", ""));
        } else {
            merged.addAll(previousRows);
        }
        return merged;
    }

    private String buildTrendDetailTitle(String kind, ReviewReport current, ReviewReport previous) {
        long prevIncome = previous == null ? 0L : previous.totalIncomeCents;
        long prevExpense = previous == null ? 0L : previous.totalExpenseCents;
        long prevWork = previous == null ? 0L : previous.totalWorkMinutes;
        if ("expense".equals(kind)) {
            return getString(R.string.review_trend_expense_compare,
                    UiFormatters.yuan(requireContext(), current.totalExpenseCents), UiFormatters.yuan(requireContext(), prevExpense));
        }
        if ("work".equals(kind)) {
            return getString(R.string.review_trend_work_compare,
                    current.totalWorkMinutes, prevWork);
        }
        return getString(R.string.review_trend_income_compare,
                UiFormatters.yuan(requireContext(), current.totalIncomeCents), UiFormatters.yuan(requireContext(), prevIncome));
    }

    private String formatCostDebtSummary(ReviewReport report) {
        String actual = UiFormatters.nullableHourly(requireContext(), report.actualHourlyRateCents);
        String ideal = UiFormatters.hourly(requireContext(), report.idealHourlyRateCents);
        String debt;
        if (report.timeDebtCents == null) {
            debt = "--";
        } else if (report.timeDebtCents > 0) {
            debt = getString(R.string.today_debt_format, UiFormatters.yuan(requireContext(), report.timeDebtCents));
        } else if (report.timeDebtCents < 0) {
            debt = getString(R.string.today_surplus_format, UiFormatters.yuan(requireContext(), Math.abs(report.timeDebtCents)));
        } else {
            debt = getString(R.string.today_balanced);
        }
        String cover = report.passiveCoverRatio == null ? "--"
                : String.format(Locale.US, "%.0f%%", report.passiveCoverRatio * 100.0);
        return getString(R.string.review_cost_debt_summary_format, actual, ideal, debt, cover);
    }

    private String formatTrendSummary(ReviewReport report) {
        String income = formatChange("Income", report.incomeChangeRatio, report.totalIncomeCents, report.prevIncomeCents);
        String expense = formatChange("Expense", report.expenseChangeRatio, report.totalExpenseCents, report.prevExpenseCents);
        String work = formatChange("Work time", report.workChangeRatio, report.totalWorkMinutes, report.prevWorkMinutes);
        return getString(R.string.review_trend_summary_format, income, expense, work);
    }

    private String formatChange(String label, Double ratio, long current, long previous) {
        if (ratio == null) {
            if (previous <= 0 && current <= 0) {
                return label + " " + getString(R.string.review_no_data_suffix);
            }
            return label + " " + getString(R.string.review_no_baseline_suffix);
        }
        String arrow = ratio > 0 ? "+" : "";
        return String.format(Locale.US, "%s %s%.1f%%", label, arrow, ratio * 100.0);
    }

    private static String formatPercentageOrDash(Double ratio) {
        if (ratio == null) {
            return "--";
        }
        return String.format(Locale.US, "%.1f%%", ratio * 100.0);
    }

    private static String formatScoreOrDash(Double score) {
        if (score == null) {
            return "--";
        }
        return String.format(Locale.US, "%.2f/10", score);
    }

    private String buildUiPeriodTitle(int tabPosition, String fallback) {
        if (customRangeEnabled) {
            return getString(R.string.review_title_custom_range);
        }
        if (tabPosition == 0) {
            return getString(R.string.review_title_daily);
        }
        if (tabPosition == 1) {
            return getString(R.string.review_title_weekly);
        }
        if (tabPosition == 2) {
            return getString(R.string.review_title_monthly);
        }
        if (tabPosition == 3) {
            return getString(R.string.review_title_yearly);
        }
        return (fallback == null || fallback.trim().isEmpty()) ? getString(R.string.nav_review) : fallback;
    }

    private String buildUiPeriodSubtitle(int tabPosition) {
        if (customRangeEnabled) {
            return getString(R.string.review_subtitle_custom_range);
        }
        if (tabPosition == 0) {
            return getString(R.string.review_subtitle_daily);
        }
        if (tabPosition == 1) {
            return getString(R.string.review_subtitle_weekly);
        }
        if (tabPosition == 2) {
            return getString(R.string.review_subtitle_monthly);
        }
        if (tabPosition == 3) {
            return getString(R.string.review_subtitle_yearly);
        }
        return getString(R.string.review_subtitle_default);
    }

    private void loadReportForCurrentTab() {
        loadReportForTab(currentTabPosition);
    }

    private void shiftAnchor(int direction) {
        if (direction == 0) {
            return;
        }
        if (customRangeEnabled) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(customStartDate, customEndDate) + 1;
            customStartDate = customStartDate.plusDays(days * direction);
            customEndDate = customEndDate.plusDays(days * direction);
        } else if (currentTabPosition == 0) {
            currentDate = currentDate.plusDays(direction);
        } else if (currentTabPosition == 1) {
            currentDate = currentDate.plusWeeks(direction);
        } else if (currentTabPosition == 2) {
            currentDate = currentDate.plusMonths(direction);
        } else {
            currentDate = currentDate.plusYears(direction);
        }
        updateAnchorUi();
        loadReportForCurrentTab();
    }

    private void updateAnchorUi() {
        if (tvReviewAnchor == null) {
            return;
        }
        if (customRangeEnabled) {
            tvReviewAnchor.setText(String.format(Locale.US, "R · %s ~ %s", customStartDate, customEndDate));
            return;
        }
        String windowLabel;
        if (currentTabPosition == 0) {
            windowLabel = "D";
        } else if (currentTabPosition == 1) {
            windowLabel = "W";
        } else if (currentTabPosition == 2) {
            windowLabel = "M";
        } else {
            windowLabel = "Y";
        }
        tvReviewAnchor.setText(String.format(Locale.US, "%s · %s", windowLabel, currentDate));
    }

    private String capitalizeType(String type) {
        if (type == null || type.isEmpty()) {
            return getString(R.string.common_all);
        }
        if ("all".equals(type)) {
            return getString(R.string.common_all);
        }
        if ("income".equals(type)) {
            return getString(R.string.common_income);
        }
        if ("expense".equals(type)) {
            return getString(R.string.common_expense);
        }
        if ("time".equals(type)) {
            return getString(R.string.common_time);
        }
        if ("learning".equals(type)) {
            return getString(R.string.common_learning);
        }
        return type;
    }

    private void openAnchorDatePicker() {
        customRangeEnabled = false;
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    currentDate = LocalDate.of(year, month + 1, dayOfMonth);
                    updateAnchorUi();
                    loadReportForCurrentTab();
                },
                currentDate.getYear(),
                currentDate.getMonthValue() - 1,
                currentDate.getDayOfMonth());
        dialog.show();
    }

    private void openAnchorDayDetail() {
        if (customRangeEnabled) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDayDetail(customStartDate.toString());
            }
            return;
        }
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openDayDetail(currentDate.toString());
        }
    }

    private void openCustomRangePicker() {
        openDatePicker(customStartDate, start -> {
            openDatePicker(customEndDate, end -> {
                LocalDate rangeStart = start;
                LocalDate rangeEnd = end;
                if (rangeEnd.isBefore(rangeStart)) {
                    LocalDate tmp = rangeStart;
                    rangeStart = rangeEnd;
                    rangeEnd = tmp;
                }
                customStartDate = rangeStart;
                customEndDate = rangeEnd;
                customRangeEnabled = true;
                updateAnchorUi();
                loadReportForCurrentTab();
            });
        });
    }

    private void openDatePicker(LocalDate initialDate, java.util.function.Consumer<LocalDate> onPicked) {
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> onPicked.accept(LocalDate.of(year, month + 1, dayOfMonth)),
                initialDate.getYear(),
                initialDate.getMonthValue() - 1,
                initialDate.getDayOfMonth());
        dialog.show();
    }
}
