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
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.WindowOverview;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class DayDetailFragment extends Fragment {
    private static final String ARG_ANCHOR_DATE = "anchor_date";

    private AppGraph graph;
    private String anchorDate;
    private TextView tvTitle;
    private TextView tvSummary;
    private TextView tvRates;
    private TextView tvNotes;
    private TextView tvRecordSummary;
    private ChipGroup chipGroupFilter;
    private RecentRecordsAdapter adapter;
    private List<RecentRecordItem> fullRows = new ArrayList<>();

    public static DayDetailFragment newInstance(String anchorDate) {
        DayDetailFragment fragment = new DayDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ANCHOR_DATE, anchorDate);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_day_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        graph = AppGraph.getInstance(requireContext());
        anchorDate = resolveAnchorDate();

        tvTitle = view.findViewById(R.id.tv_day_detail_title);
        tvSummary = view.findViewById(R.id.tv_day_detail_summary);
        tvRates = view.findViewById(R.id.tv_day_detail_rates);
        tvNotes = view.findViewById(R.id.tv_day_detail_notes);
        tvRecordSummary = view.findViewById(R.id.tv_day_detail_record_summary);
        chipGroupFilter = view.findViewById(R.id.chip_group_day_detail_filter);
        RecyclerView rvRecords = view.findViewById(R.id.rv_day_detail_records);
        rvRecords.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecentRecordsAdapter();
        rvRecords.setAdapter(adapter);
        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());
        }

        load();
    }

    private void load() {
        WindowOverview overview = graph.useCases.getOverview.execute(anchorDate, "day");
        MetricSnapshotSummary snapshot = graph.useCases.recomputeMetricSnapshot.execute(anchorDate, "day");
        fullRows = graph.useCases.getRecordsForDate.execute(anchorDate, 200);

        tvTitle.setText("Day Detail · " + anchorDate);
        tvSummary.setText(String.format(
                Locale.US,
                "Work %s | Income %s | Expense %s | Learning %s\nActual expense %s | Structural burn %s",
                formatMinutes(overview.totalWorkMinutes),
                formatYuan(overview.totalIncomeCents),
                formatYuan(overview.totalExpenseCents),
                formatMinutes(overview.totalLearningMinutes),
                formatYuan(overview.actualExpenseCents),
                formatYuan(overview.structuralExpenseCents)));
        tvRates.setText(buildRates(snapshot));
        applyFilter();
    }

    private String resolveAnchorDate() {
        Bundle args = getArguments();
        String value = args == null ? null : args.getString(ARG_ANCHOR_DATE);
        if (value == null || value.trim().isEmpty()) {
            return LocalDate.now().toString();
        }
        return value.trim();
    }

    private static String buildRates(MetricSnapshotSummary snapshot) {
        if (snapshot == null || snapshot.hourlyRateCents == null) {
            return "No hourly rate data for this day";
        }
        String debt;
        if (snapshot.timeDebtCents == null) {
            debt = "--";
        } else if (snapshot.timeDebtCents > 0) {
            debt = "Debt " + formatYuan(snapshot.timeDebtCents) + "/h";
        } else if (snapshot.timeDebtCents < 0) {
            debt = "Surplus " + formatYuan(Math.abs(snapshot.timeDebtCents)) + "/h";
        } else {
            debt = "Balanced";
        }
        return String.format(Locale.US, "Actual %s/h | %s", formatYuan(snapshot.hourlyRateCents), debt);
    }

    private static String buildNotes(List<RecentRecordItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return "No records for this day";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (RecentRecordItem item : rows) {
            if (item == null || item.detail == null || item.detail.trim().isEmpty()) {
                continue;
            }
            String note = extractNote(item.detail);
            if (note.isEmpty()) {
                continue;
            }
            sb.append("• ").append(note).append('\n');
            count++;
            if (count >= 4) {
                break;
            }
        }
        return count == 0 ? "No notes for this day" : sb.toString().trim();
    }

    private void applyFilter() {
        List<RecentRecordItem> filtered = filterRows(fullRows, selectedType());
        tvNotes.setText(buildNotes(filtered));
        adapter.submitList(filtered);
        if (tvRecordSummary != null) {
            tvRecordSummary.setText(buildRecordSummary(filtered, selectedType()));
        }
    }

    private String selectedType() {
        if (chipGroupFilter == null) {
            return "all";
        }
        int checkedId = chipGroupFilter.getCheckedChipId();
        if (checkedId == R.id.chip_day_detail_time) {
            return "time";
        }
        if (checkedId == R.id.chip_day_detail_income) {
            return "income";
        }
        if (checkedId == R.id.chip_day_detail_expense) {
            return "expense";
        }
        if (checkedId == R.id.chip_day_detail_learning) {
            return "learning";
        }
        return "all";
    }

    private static List<RecentRecordItem> filterRows(List<RecentRecordItem> rows, String type) {
        List<RecentRecordItem> out = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        if ("all".equals(type)) {
            out.addAll(rows);
            return out;
        }
        for (RecentRecordItem item : rows) {
            if (item == null || item.type == null) {
                continue;
            }
            if (type.equals(item.type)) {
                out.add(item);
            }
        }
        return out;
    }

    private static String buildRecordSummary(List<RecentRecordItem> rows, String selectedType) {
        int count = rows == null ? 0 : rows.size();
        String label = "all".equals(selectedType)
                ? "All records"
                : Character.toUpperCase(selectedType.charAt(0)) + selectedType.substring(1) + " records";
        return String.format(Locale.US, "%s · %d item%s", label, count, count == 1 ? "" : "s");
    }

    private static String extractNote(String detail) {
        int idx = detail.indexOf('|');
        String candidate = idx >= 0 ? detail.substring(idx + 1).trim() : detail.trim();
        if (candidate.matches("^[0-9]+\\s*(cents|min)?$")) {
            return "";
        }
        return candidate;
    }

    private static String formatMinutes(long minutes) {
        if (minutes <= 0) {
            return "--";
        }
        return String.format(Locale.US, "%dh %02dm", minutes / 60, minutes % 60);
    }

    private static String formatYuan(long cents) {
        if (cents == 0) {
            return "--";
        }
        if (cents % 100 == 0) {
            return String.format(Locale.US, "¥%,d", cents / 100);
        }
        return String.format(Locale.US, "¥%.2f", cents / 100.0);
    }
}
