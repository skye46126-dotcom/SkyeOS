package com.example.skyeos.ui.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.WindowOverview;
import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.model.input.CreateTimeLogInput;
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
        adapter.setOnRecordActionListener(new RecentRecordsAdapter.OnRecordActionListener() {
            @Override
            public void onRecordEdit(RecentRecordItem record) {
                showEditDialog(record);
            }

            @Override
            public void onRecordDelete(RecentRecordItem record) {
                confirmDelete(record);
            }
        });
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

        tvTitle.setText(getString(R.string.day_detail_title_with_date, anchorDate));
        tvSummary.setText(getString(
                R.string.day_detail_summary_format,
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

    private String buildRates(MetricSnapshotSummary snapshot) {
        if (snapshot == null || snapshot.hourlyRateCents == null) {
            return getString(R.string.day_detail_no_hourly_data);
        }
        String debt;
        if (snapshot.timeDebtCents == null) {
            debt = getString(R.string.common_none);
        } else if (snapshot.timeDebtCents > 0) {
            debt = getString(R.string.day_detail_debt_format, formatYuan(snapshot.timeDebtCents));
        } else if (snapshot.timeDebtCents < 0) {
            debt = getString(R.string.day_detail_surplus_format, formatYuan(Math.abs(snapshot.timeDebtCents)));
        } else {
            debt = getString(R.string.day_detail_balanced);
        }
        return getString(R.string.day_detail_rate_summary_format, formatYuan(snapshot.hourlyRateCents), debt);
    }

    private String buildNotes(List<RecentRecordItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return getString(R.string.day_detail_no_records);
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
            sb.append(getString(R.string.day_detail_notes_bullet, note)).append('\n');
            count++;
            if (count >= 4) {
                break;
            }
        }
        return count == 0 ? getString(R.string.day_detail_no_notes) : sb.toString().trim();
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

    private String buildRecordSummary(List<RecentRecordItem> rows, String selectedType) {
        int count = rows == null ? 0 : rows.size();
        String label = "all".equals(selectedType)
                ? getString(R.string.day_detail_filter_all_records)
                : getString(R.string.day_detail_filter_type_records,
                        Character.toUpperCase(selectedType.charAt(0)) + selectedType.substring(1));
        return label + " · " + getString(
                R.string.common_items_count,
                count,
                count == 1 ? "" : getString(R.string.common_plural_suffix));
    }

    private static String extractNote(String detail) {
        int idx = detail.indexOf('|');
        String candidate = idx >= 0 ? detail.substring(idx + 1).trim() : detail.trim();
        if (candidate.matches("^[0-9]+\\s*(cents|min)?$")) {
            return "";
        }
        return candidate;
    }

    private String formatMinutes(long minutes) {
        if (minutes <= 0) {
            return getString(R.string.common_none);
        }
        return getString(R.string.common_duration_hours_minutes, minutes / 60, minutes % 60);
    }

    private String formatYuan(long cents) {
        if (cents == 0) {
            return getString(R.string.common_none);
        }
        if (cents % 100 == 0) {
            return getString(R.string.common_currency_yuan_int, cents / 100);
        }
        return getString(R.string.common_currency_yuan, cents / 100.0);
    }

    private void confirmDelete(RecentRecordItem record) {
        if (record == null || record.recordId == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_delete_title)
                .setMessage(getString(R.string.day_detail_delete_message, record.type))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    try {
                        graph.useCases.deleteRecord.execute(record.type, record.recordId);
                        load();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(requireContext(), e.getMessage(), android.widget.Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .show();
    }

    private void showEditDialog(RecentRecordItem record) {
        if (record == null || record.recordId == null) {
            return;
        }
        switch (record.type) {
            case "time":
                showTimeEditDialog(record);
                break;
            case "income":
                showIncomeEditDialog(record);
                break;
            case "expense":
                showExpenseEditDialog(record);
                break;
            case "learning":
                showLearningEditDialog(record);
                break;
            default:
                break;
        }
    }

    private void showTimeEditDialog(RecentRecordItem record) {
        TimeEditState state = loadTimeState(record.recordId);
        LinearLayout layout = dialogLayout();
        EditText etStart = editField(getString(R.string.day_detail_edit_start_utc), state.startedAt, InputType.TYPE_CLASS_TEXT, layout);
        EditText etEnd = editField(getString(R.string.day_detail_edit_end_utc), state.endedAt, InputType.TYPE_CLASS_TEXT, layout);
        EditText etCategory = editField(getString(R.string.common_category), state.category, InputType.TYPE_CLASS_TEXT, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_time)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        graph.useCases.updateTimeLog.execute(record.recordId, new CreateTimeLogInput(
                                etStart.getText().toString().trim(),
                                etEnd.getText().toString().trim(),
                                etCategory.getText().toString().trim().toLowerCase(Locale.US),
                                null, null, null, null,
                                etNote.getText().toString().trim(),
                                null, null));
                        load();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(requireContext(), e.getMessage(), android.widget.Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .show();
    }

    private void showIncomeEditDialog(RecentRecordItem record) {
        IncomeEditState state = loadIncomeState(record.recordId);
        LinearLayout layout = dialogLayout();
        EditText etDate = editField(getString(R.string.common_date), state.occurredOn, InputType.TYPE_CLASS_TEXT, layout);
        EditText etSource = editField(getString(R.string.common_source), state.sourceName, InputType.TYPE_CLASS_TEXT, layout);
        EditText etType = editField(getString(R.string.common_type), state.type, InputType.TYPE_CLASS_TEXT, layout);
        EditText etAmount = editField(getString(R.string.common_amount_cny), formatDecimalYuan(state.amountCents), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_income)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        graph.useCases.updateIncome.execute(record.recordId, new CreateIncomeInput(
                                etDate.getText().toString().trim(),
                                etSource.getText().toString().trim(),
                                etType.getText().toString().trim().toLowerCase(Locale.US),
                                toCents(etAmount.getText().toString().trim()),
                                state.isPassive,
                                null,
                                etNote.getText().toString().trim(),
                                null, null));
                        load();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(requireContext(), e.getMessage(), android.widget.Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .show();
    }

    private void showExpenseEditDialog(RecentRecordItem record) {
        ExpenseEditState state = loadExpenseState(record.recordId);
        LinearLayout layout = dialogLayout();
        EditText etDate = editField(getString(R.string.common_date), state.occurredOn, InputType.TYPE_CLASS_TEXT, layout);
        EditText etCategory = editField(getString(R.string.common_category), state.category, InputType.TYPE_CLASS_TEXT, layout);
        EditText etAmount = editField(getString(R.string.common_amount_cny), formatDecimalYuan(state.amountCents), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_expense)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        graph.useCases.updateExpense.execute(record.recordId, new CreateExpenseInput(
                                etDate.getText().toString().trim(),
                                etCategory.getText().toString().trim().toLowerCase(Locale.US),
                                toCents(etAmount.getText().toString().trim()),
                                null,
                                etNote.getText().toString().trim(),
                                null, null));
                        load();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(requireContext(), e.getMessage(), android.widget.Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .show();
    }

    private void showLearningEditDialog(RecentRecordItem record) {
        LearningEditState state = loadLearningState(record.recordId);
        LinearLayout layout = dialogLayout();
        EditText etDate = editField(getString(R.string.common_date), state.occurredOn, InputType.TYPE_CLASS_TEXT, layout);
        EditText etStart = editField(getString(R.string.day_detail_edit_start_utc), state.startedAt, InputType.TYPE_CLASS_TEXT, layout);
        EditText etEnd = editField(getString(R.string.day_detail_edit_end_utc), state.endedAt, InputType.TYPE_CLASS_TEXT, layout);
        EditText etContent = editField(getString(R.string.common_content), state.content, InputType.TYPE_CLASS_TEXT, layout);
        EditText etDuration = editField(getString(R.string.day_detail_edit_duration_minutes), String.valueOf(state.durationMinutes), InputType.TYPE_CLASS_NUMBER, layout);
        EditText etLevel = editField(getString(R.string.day_detail_edit_learning_level), state.applicationLevel, InputType.TYPE_CLASS_TEXT, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_learning)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        graph.useCases.updateLearning.execute(record.recordId, new CreateLearningInput(
                                etDate.getText().toString().trim(),
                                blankToNull(etStart.getText().toString().trim()),
                                blankToNull(etEnd.getText().toString().trim()),
                                etContent.getText().toString().trim(),
                                parseInt(etDuration.getText().toString().trim()),
                                state.efficiencyScore,
                                etLevel.getText().toString().trim().toLowerCase(Locale.US),
                                state.aiAssistRatio,
                                etNote.getText().toString().trim(),
                                null, null));
                        load();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(requireContext(), e.getMessage(), android.widget.Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .show();
    }

    private LinearLayout dialogLayout() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        return layout;
    }

    private EditText editField(String hint, String value, int inputType, LinearLayout parent) {
        EditText editText = new EditText(requireContext());
        editText.setHint(hint);
        editText.setText(value == null ? "" : value);
        editText.setInputType(inputType);
        parent.addView(editText);
        return editText;
    }

    private static String safeDateFromOccurredAt(String occurredAt) {
        if (occurredAt == null || occurredAt.trim().isEmpty()) {
            return LocalDate.now().toString();
        }
        String raw = occurredAt.trim();
        if (raw.length() >= 10) {
            return raw.substring(0, 10);
        }
        return raw;
    }

    private static long toCents(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        return Math.round(Double.parseDouble(value.trim()) * 100.0);
    }

    private static int parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static ParsedRecord parseRecord(RecentRecordItem item) {
        String detail = item == null || item.detail == null ? "" : item.detail.trim();
        int idx = detail.indexOf('|');
        if (idx < 0) {
            return new ParsedRecord(detail, "");
        }
        return new ParsedRecord(detail.substring(0, idx).trim(), detail.substring(idx + 1).trim());
    }

    private static final class ParsedRecord {
        final String primary;
        final String note;

        ParsedRecord(String primary, String note) {
            this.primary = primary == null ? "" : primary;
            this.note = note == null ? "" : note;
        }

        String primaryYuan() {
            return primary.replace("cents", "").trim().matches("^[0-9]+$")
                    ? String.valueOf(Long.parseLong(primary.replace("cents", "").trim()) / 100.0)
                    : "";
        }

        String primaryMinutes() {
            return primary.replace("min", "").trim();
        }
    }

    private TimeEditState loadTimeState(String recordId) {
        try (android.database.Cursor cursor = graph.database.readableDb().rawQuery(
                "SELECT started_at, ended_at, category, COALESCE(note, '') FROM time_log WHERE id = ? LIMIT 1",
                new String[] { recordId })) {
            if (cursor.moveToFirst()) {
                return new TimeEditState(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3));
            }
        }
        return new TimeEditState(anchorDate + "T00:00:00Z", anchorDate + "T01:00:00Z", "work", "");
    }

    private IncomeEditState loadIncomeState(String recordId) {
        try (android.database.Cursor cursor = graph.database.readableDb().rawQuery(
                "SELECT occurred_on, source_name, type, amount_cents, is_passive, COALESCE(note, '') FROM income WHERE id = ? LIMIT 1",
                new String[] { recordId })) {
            if (cursor.moveToFirst()) {
                return new IncomeEditState(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3),
                        cursor.getInt(4) == 1, cursor.getString(5));
            }
        }
        ParsedRecord parsed = parseRecord(new RecentRecordItem("income", anchorDate + "T00:00:00Z", "", ""));
        return new IncomeEditState(anchorDate, "", "other", 0L, false, parsed.note);
    }

    private ExpenseEditState loadExpenseState(String recordId) {
        try (android.database.Cursor cursor = graph.database.readableDb().rawQuery(
                "SELECT occurred_on, category, amount_cents, COALESCE(note, '') FROM expense WHERE id = ? LIMIT 1",
                new String[] { recordId })) {
            if (cursor.moveToFirst()) {
                return new ExpenseEditState(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3));
            }
        }
        return new ExpenseEditState(anchorDate, "necessary", 0L, "");
    }

    private LearningEditState loadLearningState(String recordId) {
        try (android.database.Cursor cursor = graph.database.readableDb().rawQuery(
                "SELECT occurred_on, COALESCE(started_at, ''), COALESCE(ended_at, ''), content, duration_minutes, efficiency_score, application_level, ai_assist_ratio, COALESCE(note, '') FROM learning_record WHERE id = ? LIMIT 1",
                new String[] { recordId })) {
            if (cursor.moveToFirst()) {
                Integer efficiency = cursor.isNull(5) ? null : cursor.getInt(5);
                Integer ai = cursor.isNull(7) ? null : cursor.getInt(7);
                return new LearningEditState(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                        cursor.getInt(4), efficiency, cursor.getString(6), ai, cursor.getString(8));
            }
        }
        return new LearningEditState(anchorDate, "", "", "", 0, null, "input", null, "");
    }

    private static String formatDecimalYuan(long cents) {
        return String.format(Locale.US, "%.2f", cents / 100.0);
    }

    private static final class TimeEditState {
        final String startedAt;
        final String endedAt;
        final String category;
        final String note;

        TimeEditState(String startedAt, String endedAt, String category, String note) {
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.category = category;
            this.note = note;
        }
    }

    private static final class IncomeEditState {
        final String occurredOn;
        final String sourceName;
        final String type;
        final long amountCents;
        final boolean isPassive;
        final String note;

        IncomeEditState(String occurredOn, String sourceName, String type, long amountCents, boolean isPassive, String note) {
            this.occurredOn = occurredOn;
            this.sourceName = sourceName;
            this.type = type;
            this.amountCents = amountCents;
            this.isPassive = isPassive;
            this.note = note;
        }
    }

    private static final class ExpenseEditState {
        final String occurredOn;
        final String category;
        final long amountCents;
        final String note;

        ExpenseEditState(String occurredOn, String category, long amountCents, String note) {
            this.occurredOn = occurredOn;
            this.category = category;
            this.amountCents = amountCents;
            this.note = note;
        }
    }

    private static final class LearningEditState {
        final String occurredOn;
        final String startedAt;
        final String endedAt;
        final String content;
        final int durationMinutes;
        final Integer efficiencyScore;
        final String applicationLevel;
        final Integer aiAssistRatio;
        final String note;

        LearningEditState(String occurredOn, String startedAt, String endedAt, String content, int durationMinutes,
                Integer efficiencyScore, String applicationLevel, Integer aiAssistRatio, String note) {
            this.occurredOn = occurredOn;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.content = content;
            this.durationMinutes = durationMinutes;
            this.efficiencyScore = efficiencyScore;
            this.applicationLevel = applicationLevel;
            this.aiAssistRatio = aiAssistRatio;
            this.note = note;
        }
    }
}
