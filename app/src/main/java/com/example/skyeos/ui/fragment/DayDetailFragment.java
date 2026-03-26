package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

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
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import com.example.skyeos.domain.usecase.LifeOsUseCases;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.example.skyeos.R;
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.WindowOverview;
import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.model.input.CreateTimeLogInput;
import com.example.skyeos.ui.util.UiFormatters;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@AndroidEntryPoint
public class DayDetailFragment extends Fragment {

    @Inject
    CurrentUserContext userContext;

    @Inject
    LifeOsDatabase database;

    @Inject
    LifeOsUseCases useCases;
    private static final String ARG_ANCHOR_DATE = "anchor_date";

    
    private String anchorDate;
    private TextView tvTitle;
    private TextView tvSummary;
    private TextView tvRates;
    private TextView tvNotes;
    private TextView tvRecordSummary;
    private ChipGroup chipGroupFilter;
    private MaterialButton btnAddTimeLog;
    RecentRecordsAdapter adapter;
    private List<RecentRecordItem> fullRows = new ArrayList<>();
    private static final DateTimeFormatter LOCAL_DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US);

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

        anchorDate = resolveAnchorDate();

        tvTitle = view.findViewById(R.id.tv_day_detail_title);
        tvSummary = view.findViewById(R.id.tv_day_detail_summary);
        tvRates = view.findViewById(R.id.tv_day_detail_rates);
        tvNotes = view.findViewById(R.id.tv_day_detail_notes);
        tvRecordSummary = view.findViewById(R.id.tv_day_detail_record_summary);
        chipGroupFilter = view.findViewById(R.id.chip_group_day_detail_filter);
        btnAddTimeLog = view.findViewById(R.id.btn_day_detail_add_time_log);
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
        if (btnAddTimeLog != null) {
            btnAddTimeLog.setOnClickListener(v -> showAddTimeLogDialog());
        }

        load();
    }

    private void load() {
        WindowOverview overview = useCases.getOverview.execute(anchorDate, "day");
        MetricSnapshotSummary snapshot = useCases.recomputeMetricSnapshot.execute(anchorDate, "day");
        fullRows = useCases.getRecordsForDate.execute(anchorDate, 200);

        tvTitle.setText(getString(R.string.day_detail_title_with_date, anchorDate));
        tvSummary.setText(getString(
                R.string.day_detail_summary_format,
                formatMinutes(overview.totalTimeMinutes),
                formatMinutes(overview.totalWorkMinutes),
                formatMinutes(overview.totalLearningMinutes),
                formatYuan(overview.totalIncomeCents),
                formatYuan(overview.totalExpenseCents),
                formatYuan(overview.structuralExpenseCents),
                formatYuan(overview.structuralExpenseCents),
                formatYuan(overview.actualExpenseCents)));
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
        return UiFormatters.duration(requireContext(), minutes);
    }

    private String formatYuan(long cents) {
        return UiFormatters.yuan(requireContext(), cents);
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
                        useCases.deleteRecord.execute(record.type, record.recordId);
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
        
        // Setup Category Dropdown
        String[] cats = {
                getString(R.string.capture_time_category_work),
                getString(R.string.capture_time_category_learning),
                getString(R.string.capture_time_category_life),
                getString(R.string.capture_time_category_entertainment),
                getString(R.string.capture_time_category_rest),
                getString(R.string.capture_time_category_social)
        };
        String[] catVals = { "work", "learning", "life", "entertainment", "rest", "social" };
        
        com.google.android.material.textfield.MaterialAutoCompleteTextView acvCategory = dropdownField(getString(R.string.common_category), layout, cats);
        acvCategory.setText(mapInternalToLabel(state.category, cats, catVals), false);

        EditText etEfficiency = editField(getString(R.string.capture_time_efficiency_score), state.efficiencyScore == null ? "" : String.valueOf(state.efficiencyScore), InputType.TYPE_CLASS_NUMBER, layout);
        EditText etValue = editField(getString(R.string.capture_time_value_score), state.valueScore == null ? "" : String.valueOf(state.valueScore), InputType.TYPE_CLASS_NUMBER, layout);
        EditText etState = editField(getString(R.string.capture_time_state_score), state.stateScore == null ? "" : String.valueOf(state.stateScore), InputType.TYPE_CLASS_NUMBER, layout);
        EditText etAi = editField(getString(R.string.capture_time_ai_ratio), state.aiAssistRatio == null ? "" : String.valueOf(state.aiAssistRatio), InputType.TYPE_CLASS_NUMBER, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_time)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        String selectedLabel = acvCategory.getText().toString().trim();
                        String mappedCategory = mapCategoryValue(selectedLabel, cats, catVals);
                        
                        useCases.updateTimeLog.execute(record.recordId, new CreateTimeLogInput(
                                etStart.getText().toString().trim(),
                                etEnd.getText().toString().trim(),
                                mappedCategory,
                                parseOptionalScore(etEfficiency.getText().toString().trim()),
                                parseOptionalScore(etValue.getText().toString().trim()),
                                parseOptionalScore(etState.getText().toString().trim()),
                                parseOptionalPercentage(etAi.getText().toString().trim()),
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

        String[] incomeLabels = {
                getString(R.string.capture_income_type_other),
                getString(R.string.capture_income_type_salary),
                getString(R.string.capture_income_type_project),
                getString(R.string.capture_income_type_investment),
                getString(R.string.capture_income_type_system)
        };
        String[] incomeVals = { "other", "salary", "project", "investment", "system" };
        com.google.android.material.textfield.MaterialAutoCompleteTextView acvType = dropdownField(getString(R.string.common_type), layout, incomeLabels);
        acvType.setText(mapInternalToLabel(state.type, incomeLabels, incomeVals), false);

        EditText etAmount = editField(getString(R.string.common_amount_cny), formatDecimalYuan(state.amountCents), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_income)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        String typeVal = mapCategoryValue(acvType.getText().toString().trim(), incomeLabels, incomeVals);
                        useCases.updateIncome.execute(record.recordId, new CreateIncomeInput(
                                etDate.getText().toString().trim(),
                                etSource.getText().toString().trim(),
                                typeVal.toLowerCase(Locale.US),
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

        String[] expenseLabels = {
                getString(R.string.capture_expense_category_essential),
                getString(R.string.capture_expense_category_experience),
                getString(R.string.capture_expense_category_subscription),
                getString(R.string.capture_expense_category_investment)
        };
        String[] expenseVals = { "necessary", "experience", "subscription", "investment" };
        com.google.android.material.textfield.MaterialAutoCompleteTextView acvCat = dropdownField(getString(R.string.common_category), layout, expenseLabels);
        acvCat.setText(mapInternalToLabel(state.category, expenseLabels, expenseVals), false);

        EditText etAmount = editField(getString(R.string.common_amount_cny), formatDecimalYuan(state.amountCents), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_expense)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        String catVal = mapCategoryValue(acvCat.getText().toString().trim(), expenseLabels, expenseVals);
                        useCases.updateExpense.execute(record.recordId, new CreateExpenseInput(
                                etDate.getText().toString().trim(),
                                catVal,
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
        EditText etEfficiency = editField(getString(R.string.capture_learning_efficiency_score), state.efficiencyScore == null ? "" : String.valueOf(state.efficiencyScore), InputType.TYPE_CLASS_NUMBER, layout);
        EditText etAi = editField(getString(R.string.capture_learning_ai_ratio), state.aiAssistRatio == null ? "" : String.valueOf(state.aiAssistRatio), InputType.TYPE_CLASS_NUMBER, layout);

        String[] levelLabels = {
                getString(R.string.capture_learning_level_input),
                getString(R.string.capture_learning_level_applied),
                getString(R.string.capture_learning_level_result)
        };
        String[] levelVals = { "input", "applied", "result" };
        com.google.android.material.textfield.MaterialAutoCompleteTextView acvLvl = dropdownField(getString(R.string.day_detail_edit_learning_level), layout, levelLabels);
        acvLvl.setText(mapInternalToLabel(state.applicationLevel, levelLabels, levelVals), false);

        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_learning)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        String lvlVal = mapCategoryValue(acvLvl.getText().toString().trim(), levelLabels, levelVals);
                        useCases.updateLearning.execute(record.recordId, new CreateLearningInput(
                                etDate.getText().toString().trim(),
                                blankToNull(etStart.getText().toString().trim()),
                                blankToNull(etEnd.getText().toString().trim()),
                                etContent.getText().toString().trim(),
                                parseInt(etDuration.getText().toString().trim()),
                                parseOptionalScore(etEfficiency.getText().toString().trim()),
                                lvlVal,
                                parseOptionalPercentage(etAi.getText().toString().trim()),
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

    private void showAddTimeLogDialog() {
        LinearLayout layout = dialogLayout();
        EditText etStart = editField(
                getString(R.string.form_time_start_hint),
                anchorDate + " 09:00",
                InputType.TYPE_CLASS_TEXT,
                layout);
        EditText etEnd = editField(
                getString(R.string.form_time_end_hint),
                anchorDate + " 10:00",
                InputType.TYPE_CLASS_TEXT,
                layout);
        String[] cats = {
                getString(R.string.capture_time_category_life),
                getString(R.string.capture_time_category_entertainment),
                getString(R.string.capture_time_category_rest),
                getString(R.string.capture_time_category_social),
                getString(R.string.capture_time_category_work),
                getString(R.string.capture_time_category_learning)
        };
        String[] catVals = { "life", "entertainment", "rest", "social", "work", "learning" };
        com.google.android.material.textfield.MaterialAutoCompleteTextView acvCategory = dropdownField(
                getString(R.string.common_category), layout, cats);
        acvCategory.setText(cats[0], false);
        EditText etEfficiency = editField(
                getString(R.string.capture_time_efficiency_score),
                "",
                InputType.TYPE_CLASS_NUMBER,
                layout);
        EditText etValue = editField(
                getString(R.string.capture_time_value_score),
                "",
                InputType.TYPE_CLASS_NUMBER,
                layout);
        EditText etState = editField(
                getString(R.string.capture_time_state_score),
                "",
                InputType.TYPE_CLASS_NUMBER,
                layout);
        EditText etAi = editField(
                getString(R.string.capture_time_ai_ratio),
                "",
                InputType.TYPE_CLASS_NUMBER,
                layout);
        EditText etNote = editField(getString(R.string.common_note), "", InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_add_time_log_title)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        String category = mapCategoryValue(acvCategory.getText().toString().trim(), cats, catVals);
                        Integer efficiency = parseOptionalScore(etEfficiency.getText().toString().trim());
                        Integer value = parseOptionalScore(etValue.getText().toString().trim());
                        Integer state = parseOptionalScore(etState.getText().toString().trim());
                        Integer ai = parseOptionalPercentage(etAi.getText().toString().trim());
                        if ("work".equals(category) || "learning".equals(category)) {
                            if (value == null) {
                                value = 5;
                            }
                            if (state == null) {
                                state = 5;
                            }
                            if (ai == null) {
                                ai = 0;
                            }
                        }
                        useCases.createTimeLog.execute(new CreateTimeLogInput(
                                toUtcInstant(etStart.getText().toString().trim()),
                                toUtcInstant(etEnd.getText().toString().trim()),
                                category,
                                efficiency,
                                value,
                                state,
                                ai,
                                etNote.getText().toString().trim(),
                                null,
                                null));
                        load();
                        android.widget.Toast.makeText(requireContext(), R.string.capture_time_saved, android.widget.Toast.LENGTH_SHORT)
                                .show();
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

    private static Integer parseOptionalScore(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        int score = Integer.parseInt(value.trim());
        if (score < 1 || score > 10) {
            throw new IllegalArgumentException("Score must be 1-10");
        }
        return score;
    }

    private static Integer parseOptionalPercentage(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        int pct = Integer.parseInt(value.trim());
        if (pct < 0 || pct > 100) {
            throw new IllegalArgumentException("AI assist ratio must be 0-100");
        }
        return pct;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String toUtcInstant(String localDateTime) {
        return LocalDateTime.parse(localDateTime.trim(), LOCAL_DT_FMT)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toInstant()
                .toString();
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
        try (android.database.Cursor cursor = database.readableDb().rawQuery(
                "SELECT started_at, ended_at, category, efficiency_score, value_score, state_score, ai_assist_ratio, COALESCE(note, '') FROM time_log WHERE id = ? LIMIT 1",
                new String[] { recordId })) {
            if (cursor.moveToFirst()) {
                Integer efficiency = cursor.isNull(3) ? null : cursor.getInt(3);
                Integer valueScore = cursor.isNull(4) ? null : cursor.getInt(4);
                Integer stateScore = cursor.isNull(5) ? null : cursor.getInt(5);
                Integer aiRatio = cursor.isNull(6) ? null : cursor.getInt(6);
                return new TimeEditState(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        efficiency,
                        valueScore,
                        stateScore,
                        aiRatio,
                        cursor.getString(7));
            }
        }
        return new TimeEditState(anchorDate + "T00:00:00Z", anchorDate + "T01:00:00Z", "work", null, null, null, null, "");
    }

    private IncomeEditState loadIncomeState(String recordId) {
        try (android.database.Cursor cursor = database.readableDb().rawQuery(
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
        try (android.database.Cursor cursor = database.readableDb().rawQuery(
                "SELECT occurred_on, category, amount_cents, COALESCE(note, '') FROM expense WHERE id = ? LIMIT 1",
                new String[] { recordId })) {
            if (cursor.moveToFirst()) {
                return new ExpenseEditState(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3));
            }
        }
        return new ExpenseEditState(anchorDate, "necessary", 0L, "");
    }

    private LearningEditState loadLearningState(String recordId) {
        try (android.database.Cursor cursor = database.readableDb().rawQuery(
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
        final Integer efficiencyScore;
        final Integer valueScore;
        final Integer stateScore;
        final Integer aiAssistRatio;
        final String note;

        TimeEditState(String startedAt, String endedAt, String category, Integer efficiencyScore, Integer valueScore,
                Integer stateScore, Integer aiAssistRatio, String note) {
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.category = category;
            this.efficiencyScore = efficiencyScore;
            this.valueScore = valueScore;
            this.stateScore = stateScore;
            this.aiAssistRatio = aiAssistRatio;
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
    private String mapInternalToLabel(String val, String[] labels, String[] values) {
        if (val == null) return labels[0];
        String normalized = val.trim().toLowerCase(Locale.US);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(normalized)) {
                return labels[i];
            }
        }
        return labels[0];
    }

    private String mapCategoryValue(String label, String[] labels, String[] values) {
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(label)) {
                return values[i];
            }
        }
        return values[0];
    }

    private com.google.android.material.textfield.MaterialAutoCompleteTextView dropdownField(String label, LinearLayout parent, String[] options) {
        com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(requireContext(), null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox_ExposedDropdownMenu);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 32, 0, 0);
        til.setLayoutParams(lp);
        til.setHint(label);

        com.google.android.material.textfield.MaterialAutoCompleteTextView acv = new com.google.android.material.textfield.MaterialAutoCompleteTextView(til.getContext());
        acv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        acv.setInputType(InputType.TYPE_NULL);
        acv.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, options));

        til.addView(acv);
        parent.addView(til);
        return acv;
    }
}
