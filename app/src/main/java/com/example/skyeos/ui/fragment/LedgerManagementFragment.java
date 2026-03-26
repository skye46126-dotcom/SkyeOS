package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
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


import com.example.skyeos.MainActivity;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.ui.util.UiFormatters;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@AndroidEntryPoint
public class LedgerManagementFragment extends Fragment {

    @Inject
    CurrentUserContext userContext;

    @Inject
    LifeOsDatabase database;

    @Inject
    LifeOsUseCases useCases;
    private static final String ARG_TYPE = "type";

    
    private String ledgerType;
    private YearMonth currentMonth = YearMonth.now();

    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvSummary;
    private TextView tvEmpty;
    private TextInputEditText etMonth;
    RecentRecordsAdapter adapter;

    public static LedgerManagementFragment newInstance(String type) {
        LedgerManagementFragment fragment = new LedgerManagementFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ledger_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ledgerType = resolveType();

        tvTitle = view.findViewById(R.id.tv_ledger_title);
        tvSubtitle = view.findViewById(R.id.tv_ledger_subtitle);
        tvSummary = view.findViewById(R.id.tv_ledger_summary);
        tvEmpty = view.findViewById(R.id.tv_ledger_empty);
        etMonth = view.findViewById(R.id.et_ledger_month);
        RecyclerView rv = view.findViewById(R.id.rv_ledger_records);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new RecentRecordsAdapter();
        adapter.setOnRecordActionListener(new RecentRecordsAdapter.OnRecordActionListener() {
            @Override
            public void onRecordEdit(RecentRecordItem record) {
                showEditDialog(record);
            }

            @Override
            public void onRecordDelete(RecentRecordItem record) {
                if (record == null || record.recordId == null || record.recordId.trim().isEmpty()) {
                    return;
                }
                try {
                    useCases.deleteRecord.execute(record.type, record.recordId);
                    bindData();
                } catch (Exception e) {
                    String message = e == null || TextUtils.isEmpty(e.getMessage())
                            ? getString(R.string.common_operation_failed, getString(R.string.common_unknown_error))
                            : getString(R.string.common_operation_failed, e.getMessage());
                    Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
                    }
                }
        });
        rv.setAdapter(adapter);

        MaterialButton btnPrev = view.findViewById(R.id.btn_ledger_prev);
        MaterialButton btnNext = view.findViewById(R.id.btn_ledger_next);
        MaterialButton btnThisMonth = view.findViewById(R.id.btn_ledger_this_month);
        MaterialButton btnApply = view.findViewById(R.id.btn_ledger_apply);
        MaterialButton btnAddRecord = view.findViewById(R.id.btn_ledger_add_record);

        btnPrev.setOnClickListener(v -> {
            currentMonth = currentMonth.minusMonths(1);
            bindData();
        });
        btnNext.setOnClickListener(v -> {
            currentMonth = currentMonth.plusMonths(1);
            bindData();
        });
        btnThisMonth.setOnClickListener(v -> {
            currentMonth = YearMonth.now();
            bindData();
        });
        btnApply.setOnClickListener(v -> {
            currentMonth = parseMonthOrNow(text(etMonth));
            bindData();
        });
        btnAddRecord.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openCaptureType(ledgerType);
            }
        });

        bindData();
    }

    private void bindData() {
        int titleRes = "income".equals(ledgerType) ? R.string.income_mgmt_title : R.string.expense_mgmt_title;
        int subtitleRes = "income".equals(ledgerType) ? R.string.income_mgmt_subtitle : R.string.expense_mgmt_subtitle;
        int emptyRes = "income".equals(ledgerType) ? R.string.ledger_mgmt_empty_income : R.string.ledger_mgmt_empty_expense;
        tvTitle.setText(titleRes);
        tvSubtitle.setText(subtitleRes);
        tvEmpty.setText(emptyRes);
        etMonth.setText(currentMonth.toString());

        List<RecentRecordItem> all = useCases.getRecentRecords.execute(500);
        List<RecentRecordItem> filtered = new ArrayList<>();
        long totalCents = 0L;
        for (RecentRecordItem item : all) {
            if (item == null || item.type == null) {
                continue;
            }
            if (!ledgerType.equals(item.type)) {
                continue;
            }
            if (!currentMonth.equals(monthOf(item.occurredAt))) {
                continue;
            }
            filtered.add(item);
            totalCents += centsFromDetail(item.detail);
        }
        adapter.submitList(filtered);
        tvSummary.setText(getString(
                R.string.ledger_mgmt_summary_format,
                currentMonth.toString(),
                filtered.size(),
                UiFormatters.yuan(requireContext(), totalCents)));
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String resolveType() {
        String value = getArguments() == null ? null : getArguments().getString(ARG_TYPE);
        return "expense".equalsIgnoreCase(value) ? "expense" : "income";
    }

    private static String text(TextInputEditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static YearMonth parseMonthOrNow(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) {
                return YearMonth.now();
            }
            return YearMonth.parse(raw.trim());
        } catch (Exception e) {
            return YearMonth.now();
        }
    }

    private static YearMonth monthOf(String occurredAt) {
        try {
            String date = anchorDate(occurredAt);
            return YearMonth.from(LocalDate.parse(date));
        } catch (Exception e) {
            return YearMonth.now();
        }
    }

    private static String anchorDate(String occurredAt) {
        if (occurredAt == null || occurredAt.trim().isEmpty()) {
            return LocalDate.now().toString();
        }
        String raw = occurredAt.trim();
        if (raw.length() >= 10) {
            return raw.substring(0, 10);
        }
        return LocalDate.now().toString();
    }

    private static long centsFromDetail(String detail) {
        if (detail == null || detail.trim().isEmpty()) {
            return 0L;
        }
        String raw = detail.trim().toLowerCase(Locale.US);
        int idx = raw.indexOf("cents");
        if (idx <= 0) {
            return 0L;
        }
        String number = raw.substring(0, idx).trim();
        try {
            return Long.parseLong(number);
        } catch (Exception e) {
            return 0L;
        }
    }

    private void showEditDialog(RecentRecordItem record) {
        if (record == null || record.recordId == null || record.recordId.trim().isEmpty()) {
            return;
        }
        if ("income".equals(record.type)) {
            showIncomeEditDialog(record);
            return;
        }
        if ("expense".equals(record.type)) {
            showExpenseEditDialog(record);
        }
    }

    private void showIncomeEditDialog(RecentRecordItem record) {
        IncomeEditState state = loadIncomeState(record);
        LinearLayout layout = dialogLayout();
        EditText etDate = editField(getString(R.string.common_date), state.occurredOn, InputType.TYPE_CLASS_TEXT, layout);
        EditText etSource = editField(getString(R.string.common_source), state.sourceName, InputType.TYPE_CLASS_TEXT, layout);
        EditText etType = editField(getString(R.string.common_type), state.type, InputType.TYPE_CLASS_TEXT, layout);
        EditText etAmount = editField(getString(R.string.common_amount_cny), formatDecimalYuan(state.amountCents),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_income)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        useCases.updateIncome.execute(record.recordId, new CreateIncomeInput(
                                etDate.getText().toString().trim(),
                                etSource.getText().toString().trim(),
                                etType.getText().toString().trim().toLowerCase(Locale.US),
                                toCents(etAmount.getText().toString().trim()),
                                state.isPassive,
                                null,
                                etNote.getText().toString().trim(),
                                null, null));
                        bindData();
                        Snackbar.make(requireView(), R.string.ledger_mgmt_record_saved, Snackbar.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        String message = e == null || TextUtils.isEmpty(e.getMessage())
                                ? getString(R.string.common_operation_failed, getString(R.string.common_unknown_error))
                                : getString(R.string.common_operation_failed, e.getMessage());
                        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showExpenseEditDialog(RecentRecordItem record) {
        ExpenseEditState state = loadExpenseState(record);
        LinearLayout layout = dialogLayout();
        EditText etDate = editField(getString(R.string.common_date), state.occurredOn, InputType.TYPE_CLASS_TEXT, layout);
        EditText etCategory = editField(getString(R.string.common_category), state.category, InputType.TYPE_CLASS_TEXT, layout);
        EditText etAmount = editField(getString(R.string.common_amount_cny), formatDecimalYuan(state.amountCents),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etNote = editField(getString(R.string.common_note), state.note, InputType.TYPE_CLASS_TEXT, layout);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_detail_edit_expense)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        useCases.updateExpense.execute(record.recordId, new CreateExpenseInput(
                                etDate.getText().toString().trim(),
                                etCategory.getText().toString().trim().toLowerCase(Locale.US),
                                toCents(etAmount.getText().toString().trim()),
                                null,
                                etNote.getText().toString().trim(),
                                null, null));
                        bindData();
                        Snackbar.make(requireView(), R.string.ledger_mgmt_record_saved, Snackbar.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        String message = e == null || TextUtils.isEmpty(e.getMessage())
                                ? getString(R.string.common_operation_failed, getString(R.string.common_unknown_error))
                                : getString(R.string.common_operation_failed, e.getMessage());
                        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private IncomeEditState loadIncomeState(RecentRecordItem record) {
        try (android.database.Cursor cursor = database.readableDb().rawQuery(
                "SELECT occurred_on, source_name, type, amount_cents, is_passive, COALESCE(note, '') FROM income WHERE id = ? LIMIT 1",
                new String[] { record.recordId })) {
            if (cursor.moveToFirst()) {
                return new IncomeEditState(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3),
                        cursor.getInt(4) == 1, cursor.getString(5));
            }
        }
        ParsedRecord parsed = parseRecord(record == null ? "" : record.detail);
        return new IncomeEditState(anchorDate(record == null ? null : record.occurredAt), "", "other",
                centsFromDetail(record == null ? null : record.detail), false, parsed.note);
    }

    private ExpenseEditState loadExpenseState(RecentRecordItem record) {
        try (android.database.Cursor cursor = database.readableDb().rawQuery(
                "SELECT occurred_on, category, amount_cents, COALESCE(note, '') FROM expense WHERE id = ? LIMIT 1",
                new String[] { record.recordId })) {
            if (cursor.moveToFirst()) {
                return new ExpenseEditState(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3));
            }
        }
        ParsedRecord parsed = parseRecord(record == null ? "" : record.detail);
        return new ExpenseEditState(anchorDate(record == null ? null : record.occurredAt), "necessary",
                centsFromDetail(record == null ? null : record.detail), parsed.note);
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

    private static long toCents(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        return Math.round(Double.parseDouble(value.trim()) * 100.0);
    }

    private static String formatDecimalYuan(long cents) {
        return String.format(Locale.US, "%.2f", cents / 100.0);
    }

    private static ParsedRecord parseRecord(String detail) {
        if (detail == null || detail.trim().isEmpty()) {
            return new ParsedRecord("", "");
        }
        String raw = detail.trim();
        int idx = raw.indexOf('|');
        if (idx < 0) {
            return new ParsedRecord(raw, "");
        }
        return new ParsedRecord(raw.substring(0, idx).trim(), raw.substring(idx + 1).trim());
    }

    private static final class ParsedRecord {
        final String primary;
        final String note;

        ParsedRecord(String primary, String note) {
            this.primary = primary == null ? "" : primary;
            this.note = note == null ? "" : note;
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
}
