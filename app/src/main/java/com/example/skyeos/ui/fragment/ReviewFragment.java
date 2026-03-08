package com.example.skyeos.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.ReviewReport;
import com.example.skyeos.domain.usecase.LifeOsUseCases;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReviewFragment extends Fragment {

    private LifeOsUseCases useCases;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TabLayout tabReviewPeriod;
    private TextView tvReviewTitle;
    private TextView tvAiSummary;
    private TextView tvCostDebtSummary;
    private TextView tvTrendSummary;
    private TextView tvTrendDetailTitle;

    private RecyclerView rvTimeAllocation;
    private TimeAllocationAdapter timeAdapter;

    private RecyclerView rvTopProjects;
    private ReviewProjectAdapter topProjectAdapter;

    private RecyclerView rvSinkholeProjects;
    private ReviewProjectAdapter sinkholeAdapter;

    private RecyclerView rvKeyEvents;
    private RecentRecordsAdapter keyEventsAdapter;
    private TextView tvIncomeHistorySummary;
    private RecyclerView rvIncomeHistory;
    private RecentRecordsAdapter incomeHistoryAdapter;
    private ChipGroup chipGroupTrendDetail;
    private RecyclerView rvTrendDetailRecords;
    private RecentRecordsAdapter trendDetailAdapter;
    private ChipGroup chipGroupHistoryFilter;
    private TextView tvHistoryLedgerSummary;
    private RecyclerView rvHistoryLedger;
    private RecentRecordsAdapter historyLedgerAdapter;
    private RecyclerView rvTagTimeMetrics;
    private RecyclerView rvTagExpenseMetrics;
    private RecyclerView rvTagDetailRecords;
    private TextView tvTagDetailTitle;
    private TagMetricAdapter timeTagAdapter;
    private TagMetricAdapter expenseTagAdapter;
    private RecentRecordsAdapter tagDetailAdapter;
    private List<com.example.skyeos.domain.model.RecentRecordItem> latestHistoryRecords = new ArrayList<>();
    private int currentTabPosition = 1;
    private ReviewReport latestReport;

    private LocalDate currentDate = LocalDate.now();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_review, container, false);
        useCases = AppGraph.getInstance(requireContext()).useCases;

        tvReviewTitle = view.findViewById(R.id.tv_review_title);
        tvAiSummary = view.findViewById(R.id.tv_ai_summary);
        tvCostDebtSummary = view.findViewById(R.id.tv_cost_debt_summary);
        tvTrendSummary = view.findViewById(R.id.tv_trend_summary);
        tvTrendDetailTitle = view.findViewById(R.id.tv_trend_detail_title);

        tabReviewPeriod = view.findViewById(R.id.tab_review_period);
        tabReviewPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTabPosition = tab.getPosition();
                loadReportForTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                currentTabPosition = tab.getPosition();
                loadReportForTab(tab.getPosition());
            }
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
        historyLedgerAdapter = new RecentRecordsAdapter();
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

        // Initial load (Weekly by default makes sense, but we can stick to Daily which
        // is index 0)
        // Let's default to index 1 (Weekly) if you want a larger perspective.
        tabReviewPeriod.selectTab(tabReviewPeriod.getTabAt(1));

        return view;
    }

    private void loadReportForTab(int tabPosition) {
        executor.execute(() -> {
            ReviewReport report = null;
            try {
                if (tabPosition == 0) {
                    report = useCases.reviewUseCases.getDailyReview(currentDate);
                } else if (tabPosition == 1) {
                    report = useCases.reviewUseCases.getWeeklyReview(currentDate);
                } else if (tabPosition == 2) {
                    report = useCases.reviewUseCases.getMonthlyReview(currentDate);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final ReviewReport finalReport = report;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (finalReport != null) {
                        displayReport(finalReport);
                    }
                });
            }
        });
    }

    private void displayReport(ReviewReport report) {
        latestReport = report;
        tvReviewTitle.setText(report.periodName);
        tvAiSummary.setText(report.aiSummary);
        tvCostDebtSummary.setText(formatCostDebtSummary(report));
        tvTrendSummary.setText(formatTrendSummary(report));

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

    private static String buildIncomeSummary(
            List<com.example.skyeos.domain.model.RecentRecordItem> incomeItems,
            long totalCents
    ) {
        double yuan = totalCents / 100.0;
        int count = incomeItems == null ? 0 : incomeItems.size();
        return String.format(Locale.US, "本期收入 %d 条 | 合计 ¥%.2f", count, yuan);
    }

    private void applyHistoryFilter() {
        if (historyLedgerAdapter == null) {
            return;
        }
        String selectedType = selectedHistoryType();
        List<com.example.skyeos.domain.model.RecentRecordItem> filtered = filterByType(latestHistoryRecords, selectedType);
        historyLedgerAdapter.submitList(filtered);
        if (tvHistoryLedgerSummary != null) {
            String label = "all".equals(selectedType) ? "全部" : selectedType;
            tvHistoryLedgerSummary.setText(String.format(Locale.US, "%s %d 条", label, filtered.size()));
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
            tvTagDetailTitle.setText("标签明细：暂无可展示标签");
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
            String emoji = metric.emoji == null || metric.emoji.isEmpty() ? "" : metric.emoji + " ";
            tvTagDetailTitle.setText("标签明细：" + emoji + metric.tagName + "（" + scope + "）");
        }
        executor.execute(() -> {
            List<com.example.skyeos.domain.model.RecentRecordItem> rows;
            if (currentTabPosition == 0) {
                rows = useCases.reviewUseCases.getDailyTagDetail(currentDate, scope, metric.tagName, 20);
            } else if (currentTabPosition == 1) {
                rows = useCases.reviewUseCases.getWeeklyTagDetail(currentDate, scope, metric.tagName, 20);
            } else {
                rows = useCases.reviewUseCases.getMonthlyTagDetail(currentDate, scope, metric.tagName, 20);
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
                tvTrendDetailTitle.setText("趋势明细：暂无数据");
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
            if (currentTabPosition == 0) {
                return useCases.reviewUseCases.getDailyReview(currentDate.minusDays(1));
            }
            if (currentTabPosition == 1) {
                return useCases.reviewUseCases.getWeeklyReview(currentDate.minusWeeks(1));
            }
            return useCases.reviewUseCases.getMonthlyReview(currentDate.minusMonths(1));
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
        merged.add(new com.example.skyeos.domain.model.RecentRecordItem("meta", "", "当前窗口", "最近记录"));
        if (currentRows == null || currentRows.isEmpty()) {
            merged.add(new com.example.skyeos.domain.model.RecentRecordItem("meta", "", "无记录", ""));
        } else {
            merged.addAll(currentRows);
        }
        merged.add(new com.example.skyeos.domain.model.RecentRecordItem("meta", "", "上一窗口", "最近记录"));
        if (previousRows == null || previousRows.isEmpty()) {
            merged.add(new com.example.skyeos.domain.model.RecentRecordItem("meta", "", "无记录", ""));
        } else {
            merged.addAll(previousRows);
        }
        return merged;
    }

    private static String buildTrendDetailTitle(String kind, ReviewReport current, ReviewReport previous) {
        long prevIncome = previous == null ? 0L : previous.totalIncomeCents;
        long prevExpense = previous == null ? 0L : previous.totalExpenseCents;
        long prevWork = previous == null ? 0L : previous.totalWorkMinutes;
        if ("expense".equals(kind)) {
            return String.format(Locale.CHINESE, "支出：当前 %s vs 上一窗口 %s",
                    formatYuan(current.totalExpenseCents), formatYuan(prevExpense));
        }
        if ("work".equals(kind)) {
            return String.format(Locale.CHINESE, "工作时长：当前 %d 分钟 vs 上一窗口 %d 分钟",
                    current.totalWorkMinutes, prevWork);
        }
        return String.format(Locale.CHINESE, "收入：当前 %s vs 上一窗口 %s",
                formatYuan(current.totalIncomeCents), formatYuan(prevIncome));
    }

    private static String formatCostDebtSummary(ReviewReport report) {
        String actual = report.actualHourlyRateCents == null ? "--" : formatYuan(report.actualHourlyRateCents) + "/h";
        String ideal = formatYuan(report.idealHourlyRateCents) + "/h";
        String debt;
        if (report.timeDebtCents == null) {
            debt = "--";
        } else if (report.timeDebtCents > 0) {
            debt = "负债 " + formatYuan(report.timeDebtCents) + "/h";
        } else if (report.timeDebtCents < 0) {
            debt = "盈余 " + formatYuan(Math.abs(report.timeDebtCents)) + "/h";
        } else {
            debt = "平衡";
        }
        String cover = report.passiveCoverRatio == null ? "--"
                : String.format(Locale.US, "%.0f%%", report.passiveCoverRatio * 100.0);
        return String.format(Locale.US, "实际时薪 %s | 理想时薪 %s | 时间%s | 被动覆盖 %s", actual, ideal, debt, cover);
    }

    private static String formatTrendSummary(ReviewReport report) {
        String income = formatChange("收入", report.incomeChangeRatio, report.totalIncomeCents, report.prevIncomeCents);
        String expense = formatChange("支出", report.expenseChangeRatio, report.totalExpenseCents, report.prevExpenseCents);
        String work = formatChange("工作时长", report.workChangeRatio, report.totalWorkMinutes, report.prevWorkMinutes);
        return "环比：" + income + " | " + expense + " | " + work;
    }

    private static String formatChange(String label, Double ratio, long current, long previous) {
        if (ratio == null) {
            if (previous <= 0 && current <= 0) {
                return label + " 暂无数据";
            }
            return label + " 无可比基线";
        }
        String arrow = ratio > 0 ? "+" : "";
        return String.format(Locale.US, "%s %s%.1f%%", label, arrow, ratio * 100.0);
    }

    private static String formatYuan(long cents) {
        if (cents % 100 == 0) {
            return String.format(Locale.US, "¥%d", cents / 100);
        }
        return String.format(Locale.US, "¥%.2f", cents / 100.0);
    }
}
