package com.example.skyeos.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.CapexCostSummary;
import com.example.skyeos.domain.model.MonthlyCostBaseline;
import com.example.skyeos.domain.model.RateComparisonSummary;
import com.example.skyeos.domain.model.RecurringCostRuleSummary;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

public class CostManagementFragment extends Fragment {

    private AppGraph graph;
    private TextView tvIdeal;
    private TextView tvPreviousYear;
    private TextView tvActual;
    private TextView tvRateMeta;
    private TextView tvBaseline;
    private TextView tvRecurring;
    private TextView tvCapex;
    private TextInputEditText etMonth;
    private TextInputEditText etIdeal;
    private TextInputEditText etMonthlyBasic;
    private TextInputEditText etMonthlyFixed;
    private TextInputEditText etRecurringName;
    private TextInputEditText etRecurringCategory;
    private TextInputEditText etRecurringAmount;
    private TextInputEditText etRecurringStartMonth;
    private TextInputEditText etRecurringEndMonth;
    private TextInputEditText etRecurringNote;
    private SwitchMaterial swRecurringNecessary;
    private TextInputEditText etCapexName;
    private TextInputEditText etCapexPurchaseDate;
    private TextInputEditText etCapexAmount;
    private TextInputEditText etCapexUsefulMonths;
    private TextInputEditText etCapexResidualBps;
    private TextInputEditText etCapexNote;
    private TextView tvToggleBaseline;
    private TextView tvToggleRecurring;
    private TextView tvToggleCapex;
    private View baselineContent;
    private View recurringContent;
    private View capexContent;
    private LinearLayout recurringItemsContainer;
    private LinearLayout capexItemsContainer;
    private String editingRecurringId;
    private String editingCapexId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cost_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        graph = AppGraph.getInstance(requireContext());

        tvIdeal = view.findViewById(R.id.tv_cost_ideal_rate);
        tvPreviousYear = view.findViewById(R.id.tv_cost_previous_year_rate);
        tvActual = view.findViewById(R.id.tv_cost_actual_rate);
        tvRateMeta = view.findViewById(R.id.tv_cost_rate_meta);
        tvBaseline = view.findViewById(R.id.tv_cost_monthly_baseline);
        tvRecurring = view.findViewById(R.id.tv_recurring_list);
        tvCapex = view.findViewById(R.id.tv_cost_capex_list);
        etMonth = view.findViewById(R.id.et_cost_month);
        etIdeal = view.findViewById(R.id.et_cost_ideal_rate);
        etMonthlyBasic = view.findViewById(R.id.et_cost_monthly_basic);
        etMonthlyFixed = view.findViewById(R.id.et_cost_monthly_fixed);
        etRecurringName = view.findViewById(R.id.et_recurring_name);
        etRecurringCategory = view.findViewById(R.id.et_recurring_category);
        etRecurringAmount = view.findViewById(R.id.et_recurring_amount);
        etRecurringStartMonth = view.findViewById(R.id.et_recurring_start_month);
        etRecurringEndMonth = view.findViewById(R.id.et_recurring_end_month);
        etRecurringNote = view.findViewById(R.id.et_recurring_note);
        swRecurringNecessary = view.findViewById(R.id.sw_recurring_necessary);
        etCapexName = view.findViewById(R.id.et_capex_name);
        etCapexPurchaseDate = view.findViewById(R.id.et_capex_purchase_date);
        etCapexAmount = view.findViewById(R.id.et_capex_amount);
        etCapexUsefulMonths = view.findViewById(R.id.et_capex_useful_months);
        etCapexResidualBps = view.findViewById(R.id.et_capex_residual_bps);
        etCapexNote = view.findViewById(R.id.et_capex_note);
        tvToggleBaseline = view.findViewById(R.id.tv_toggle_baseline);
        tvToggleRecurring = view.findViewById(R.id.tv_toggle_recurring);
        tvToggleCapex = view.findViewById(R.id.tv_toggle_capex);
        baselineContent = view.findViewById(R.id.layout_baseline_content);
        recurringContent = view.findViewById(R.id.layout_recurring_content);
        capexContent = view.findViewById(R.id.layout_capex_content);
        recurringItemsContainer = view.findViewById(R.id.container_recurring_items);
        capexItemsContainer = view.findViewById(R.id.container_capex_items);

        if (text(etMonth).isEmpty()) {
            etMonth.setText(YearMonth.now().toString());
        }
        if (text(etRecurringCategory).isEmpty()) {
            etRecurringCategory.setText(R.string.cost_subscription_default);
        }
        if (swRecurringNecessary != null) {
            swRecurringNecessary.setChecked(false);
        }
        if (text(etRecurringStartMonth).isEmpty()) {
            etRecurringStartMonth.setText(YearMonth.now().toString());
        }
        if (text(etCapexPurchaseDate).isEmpty()) {
            etCapexPurchaseDate.setText(LocalDate.now().toString());
        }
        if (text(etCapexUsefulMonths).isEmpty()) {
            etCapexUsefulMonths.setText(R.string.cost_default_useful_months);
        }

        View btnSave = view.findViewById(R.id.btn_save_cost_inputs);
        View btnAddRecurring = view.findViewById(R.id.btn_add_recurring);
        View btnAddCapex = view.findViewById(R.id.btn_add_capex);
        bindToggle(tvToggleBaseline, baselineContent, R.string.cost_monthly_baseline_collapsed, R.string.cost_monthly_baseline_open);
        bindToggle(tvToggleRecurring, recurringContent, R.string.cost_recurring_collapsed, R.string.cost_recurring_open);
        bindToggle(tvToggleCapex, capexContent, R.string.cost_capex_collapsed, R.string.cost_capex_open);

        bindData();

        btnSave.setOnClickListener(v -> {
            long idealValue = parseYuanToCents(text(etIdeal));
            long basicValue = parseYuanToCents(text(etMonthlyBasic));
            long fixedValue = parseYuanToCents(text(etMonthlyFixed));
            String month = normalizeMonthOrFallback(text(etMonth));
            graph.useCases.setIdealHourlyRate.execute(idealValue);
            graph.useCases.upsertMonthlyCostBaseline.execute(month, basicValue, fixedValue);
            Snackbar.make(view, R.string.cost_saved, Snackbar.LENGTH_SHORT).show();
            bindData();
        });

        btnAddRecurring.setOnClickListener(v -> {
            if (editingRecurringId == null) {
                graph.useCases.createRecurringCostRule.execute(
                        text(etRecurringName),
                        text(etRecurringCategory),
                        parseYuanToCents(text(etRecurringAmount)),
                        swRecurringNecessary != null && swRecurringNecessary.isChecked(),
                        normalizeMonthOrFallback(text(etRecurringStartMonth)),
                        text(etRecurringEndMonth).isEmpty() ? null : normalizeMonthOrFallback(text(etRecurringEndMonth)),
                        text(etRecurringNote));
            } else {
                graph.useCases.updateRecurringCostRule.execute(
                        editingRecurringId,
                        text(etRecurringName),
                        text(etRecurringCategory),
                        parseYuanToCents(text(etRecurringAmount)),
                        swRecurringNecessary != null && swRecurringNecessary.isChecked(),
                        normalizeMonthOrFallback(text(etRecurringStartMonth)),
                        text(etRecurringEndMonth).isEmpty() ? null : normalizeMonthOrFallback(text(etRecurringEndMonth)),
                        text(etRecurringNote));
            }
            clearRecurringInputs();
            Snackbar.make(view, editingRecurringId == null ? R.string.cost_recurring_added : R.string.cost_recurring_updated, Snackbar.LENGTH_SHORT).show();
            editingRecurringId = null;
            ((TextView) btnAddRecurring).setText(R.string.cost_add_recurring_rule);
            bindData();
        });

        btnAddCapex.setOnClickListener(v -> {
            if (editingCapexId == null) {
                graph.useCases.createCapexCost.execute(
                        text(etCapexName),
                        normalizeDateOrToday(text(etCapexPurchaseDate)),
                        parseYuanToCents(text(etCapexAmount)),
                        parseIntOrDefault(text(etCapexUsefulMonths), 12),
                        parseIntOrDefault(text(etCapexResidualBps), 0),
                        text(etCapexNote));
            } else {
                graph.useCases.updateCapexCost.execute(
                        editingCapexId,
                        text(etCapexName),
                        normalizeDateOrToday(text(etCapexPurchaseDate)),
                        parseYuanToCents(text(etCapexAmount)),
                        parseIntOrDefault(text(etCapexUsefulMonths), 12),
                        parseIntOrDefault(text(etCapexResidualBps), 0),
                        text(etCapexNote));
            }
            clearCapexInputs();
            Snackbar.make(view, editingCapexId == null ? R.string.cost_capex_added : R.string.cost_capex_updated, Snackbar.LENGTH_SHORT).show();
            editingCapexId = null;
            ((TextView) btnAddCapex).setText(R.string.cost_add_capex_item);
            bindData();
        });
    }

    private void bindToggle(TextView toggle, View content, int collapsedText, int expandedText) {
        if (toggle == null || content == null) {
            return;
        }
        toggle.setOnClickListener(v -> {
            boolean expand = content.getVisibility() != View.VISIBLE;
            content.setVisibility(expand ? View.VISIBLE : View.GONE);
            toggle.setText(expand ? expandedText : collapsedText);
        });
    }

    private void bindData() {
        String month = normalizeMonthOrFallback(text(etMonth));
        RateComparisonSummary rates = graph.useCases.getRateComparison.execute(YearMonth.parse(month).atEndOfMonth().toString(), "month");
        MonthlyCostBaseline baseline = graph.useCases.getMonthlyCostBaseline.execute(month);
        List<RecurringCostRuleSummary> recurringRules = graph.useCases.listRecurringCostRules.execute();
        List<CapexCostSummary> capexCosts = graph.useCases.listCapexCosts.execute();

        tvIdeal.setText(formatHourly(rates.idealHourlyRateCents));
        tvPreviousYear.setText(formatNullableHourly(rates.previousYearAverageHourlyRateCents));
        tvActual.setText(formatNullableHourly(rates.actualHourlyRateCents));
        tvRateMeta.setText(getString(
                R.string.cost_rate_meta_format,
                formatYuan(nullableLong(rates.currentIncomeCents)),
                formatMinutes(rates.currentWorkMinutes),
                formatYuan(nullableLong(rates.previousYearIncomeCents)),
                formatMinutes(rates.previousYearWorkMinutes)));
        tvBaseline.setText(getString(R.string.cost_baseline_summary_format,
                month, formatYuan(baseline.basicLivingCents), formatYuan(baseline.fixedSubscriptionCents)));
        tvRecurring.setText(buildRecurringSummary(recurringRules));
        tvCapex.setText(buildCapexSummary(capexCosts));
        renderRecurringItems(recurringRules);
        renderCapexItems(capexCosts);
        etMonth.setText(month);
        etIdeal.setText(toEditableYuan(rates.idealHourlyRateCents));
        etMonthlyBasic.setText(toEditableYuan(baseline.basicLivingCents));
        etMonthlyFixed.setText(toEditableYuan(baseline.fixedSubscriptionCents));
    }

    private String buildRecurringSummary(List<RecurringCostRuleSummary> recurringRules) {
        StringBuilder sb = new StringBuilder();
        if (recurringRules == null || recurringRules.isEmpty()) {
            sb.append(getString(R.string.cost_no_recurring_rules));
        } else {
            int limit = Math.min(3, recurringRules.size());
            for (int i = 0; i < limit; i++) {
                RecurringCostRuleSummary item = recurringRules.get(i);
                String necessity = item.necessary ? getString(R.string.common_necessary) : getString(R.string.common_optional);
                sb.append(item.endMonth != null && !item.endMonth.isEmpty()
                        ? getString(R.string.cost_recurring_summary_line_with_end, item.name, formatYuan(item.monthlyAmountCents), item.necessary ? " | necessary" : " | optional", item.startMonth, item.endMonth)
                        : getString(R.string.cost_recurring_summary_line, item.name, formatYuan(item.monthlyAmountCents), item.necessary ? " | necessary" : " | optional", item.startMonth));
                sb = new StringBuilder(sb.toString().replace(item.necessary ? "necessary" : "optional", necessity));
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String buildCapexSummary(List<CapexCostSummary> capexCosts) {
        StringBuilder sb = new StringBuilder();
        if (capexCosts == null || capexCosts.isEmpty()) {
            sb.append(getString(R.string.cost_no_capex_items));
        } else {
            int limit = Math.min(3, capexCosts.size());
            for (int i = 0; i < limit; i++) {
                CapexCostSummary item = capexCosts.get(i);
                sb.append(getString(R.string.cost_capex_summary_line, item.name, formatYuan(item.monthlyAmortizedCents), item.amortizationStartMonth, item.amortizationEndMonth));
                if (i < limit - 1) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString().trim();
    }

    private void clearRecurringInputs() {
        etRecurringName.setText("");
        etRecurringCategory.setText(R.string.cost_subscription_default);
        etRecurringAmount.setText("");
        etRecurringEndMonth.setText("");
        etRecurringNote.setText("");
        if (swRecurringNecessary != null) {
            swRecurringNecessary.setChecked(false);
        }
    }

    private void clearCapexInputs() {
        etCapexName.setText("");
        etCapexPurchaseDate.setText(LocalDate.now().toString());
        etCapexAmount.setText("");
        etCapexUsefulMonths.setText(R.string.cost_default_useful_months);
        etCapexResidualBps.setText("");
        etCapexNote.setText("");
    }

    private void renderRecurringItems(List<RecurringCostRuleSummary> items) {
        if (recurringItemsContainer == null) {
            return;
        }
        recurringItemsContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RecurringCostRuleSummary item : items) {
            View row = inflater.inflate(R.layout.item_cost_action, recurringItemsContainer, false);
            TextView title = row.findViewById(R.id.tv_cost_item_title);
            TextView meta = row.findViewById(R.id.tv_cost_item_meta);
            TextView note = row.findViewById(R.id.tv_cost_item_note);
            View edit = row.findViewById(R.id.btn_cost_item_edit);
            View delete = row.findViewById(R.id.btn_cost_item_delete);
            title.setText(getString(R.string.cost_item_title_monthly, item.name, formatYuan(item.monthlyAmountCents)));
            meta.setText(getString(R.string.cost_item_meta_recurring,
                    item.category,
                    item.necessary ? getString(R.string.common_necessary) : getString(R.string.common_optional),
                    item.startMonth,
                    item.endMonth == null || item.endMonth.isEmpty() ? "" : getString(R.string.cost_item_end_month_format, item.endMonth)));
            if (item.note != null && !item.note.trim().isEmpty()) {
                note.setText(item.note);
                note.setVisibility(View.VISIBLE);
            }
            edit.setOnClickListener(v -> {
                editingRecurringId = item.id;
                etRecurringName.setText(item.name);
                etRecurringCategory.setText(item.category);
                etRecurringAmount.setText(toEditableYuan(item.monthlyAmountCents));
                etRecurringStartMonth.setText(item.startMonth);
                etRecurringEndMonth.setText(item.endMonth == null ? "" : item.endMonth);
                etRecurringNote.setText(item.note == null ? "" : item.note);
                if (swRecurringNecessary != null) {
                    swRecurringNecessary.setChecked(item.necessary);
                }
                TextView btn = requireView().findViewById(R.id.btn_add_recurring);
                btn.setText(R.string.cost_save_recurring_changes);
                if (recurringContent != null) {
                    recurringContent.setVisibility(View.VISIBLE);
                }
                if (tvToggleRecurring != null) {
                    tvToggleRecurring.setText(R.string.cost_recurring_open);
                }
            });
            delete.setOnClickListener(v -> {
                graph.useCases.deleteRecurringCostRule.execute(item.id);
                if (item.id.equals(editingRecurringId)) {
                    editingRecurringId = null;
                    clearRecurringInputs();
                    TextView btn = requireView().findViewById(R.id.btn_add_recurring);
                    btn.setText(R.string.cost_add_recurring_rule);
                }
                Snackbar.make(requireView(), R.string.cost_recurring_deleted, Snackbar.LENGTH_SHORT).show();
                bindData();
            });
            recurringItemsContainer.addView(row);
        }
    }

    private void renderCapexItems(List<CapexCostSummary> items) {
        if (capexItemsContainer == null) {
            return;
        }
        capexItemsContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (CapexCostSummary item : items) {
            View row = inflater.inflate(R.layout.item_cost_action, capexItemsContainer, false);
            TextView title = row.findViewById(R.id.tv_cost_item_title);
            TextView meta = row.findViewById(R.id.tv_cost_item_meta);
            TextView note = row.findViewById(R.id.tv_cost_item_note);
            View edit = row.findViewById(R.id.btn_cost_item_edit);
            View delete = row.findViewById(R.id.btn_cost_item_delete);
            title.setText(getString(R.string.cost_item_title_monthly, item.name, formatYuan(item.monthlyAmortizedCents)));
            meta.setText(getString(R.string.cost_item_meta_purchase,
                    item.purchaseDate,
                    formatYuan(item.purchaseAmountCents),
                    item.amortizationStartMonth,
                    item.amortizationEndMonth));
            if (item.note != null && !item.note.trim().isEmpty()) {
                note.setText(item.note);
                note.setVisibility(View.VISIBLE);
            }
            edit.setOnClickListener(v -> {
                editingCapexId = item.id;
                etCapexName.setText(item.name);
                etCapexPurchaseDate.setText(item.purchaseDate);
                etCapexAmount.setText(toEditableYuan(item.purchaseAmountCents));
                etCapexUsefulMonths.setText(String.valueOf(item.usefulMonths));
                etCapexResidualBps.setText(item.residualRateBps <= 0 ? "" : String.valueOf(item.residualRateBps));
                etCapexNote.setText(item.note == null ? "" : item.note);
                TextView btn = requireView().findViewById(R.id.btn_add_capex);
                btn.setText(R.string.cost_save_capex_changes);
                if (capexContent != null) {
                    capexContent.setVisibility(View.VISIBLE);
                }
                if (tvToggleCapex != null) {
                    tvToggleCapex.setText(R.string.cost_capex_open);
                }
            });
            delete.setOnClickListener(v -> {
                graph.useCases.deleteCapexCost.execute(item.id);
                if (item.id.equals(editingCapexId)) {
                    editingCapexId = null;
                    clearCapexInputs();
                    TextView btn = requireView().findViewById(R.id.btn_add_capex);
                    btn.setText(R.string.cost_add_capex_item);
                }
                Snackbar.make(requireView(), R.string.cost_capex_deleted, Snackbar.LENGTH_SHORT).show();
                bindData();
            });
            capexItemsContainer.addView(row);
        }
    }

    private String formatHourly(long cents) {
        return getString(R.string.common_hourly_format, formatYuan(cents));
    }

    private String formatNullableHourly(Long cents) {
        return cents == null ? getString(R.string.common_none) : formatHourly(cents);
    }

    private String formatMinutes(Long minutes) {
        if (minutes == null || minutes <= 0L) {
            return getString(R.string.common_none);
        }
        return getString(R.string.common_duration_hours_minutes, minutes / 60, minutes % 60);
    }

    private static long nullableLong(Long value) {
        return value == null ? 0L : value;
    }

    private static String toEditableYuan(long cents) {
        if (cents <= 0L) {
            return "";
        }
        return String.format(Locale.US, "%.2f", cents / 100.0);
    }

    private static String text(TextInputEditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static long parseYuanToCents(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) {
                return 0L;
            }
            return Math.round(Double.parseDouble(raw.trim()) * 100.0);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        try {
            if (raw == null || raw.trim().isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
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

    private static String normalizeDateOrToday(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) {
                return LocalDate.now().toString();
            }
            return LocalDate.parse(raw.trim()).toString();
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    private String formatYuan(long cents) {
        if (cents == 0L) {
            return getString(R.string.common_none);
        }
        if (cents % 100 == 0) {
            return getString(R.string.common_currency_yuan_int, cents / 100);
        }
        return getString(R.string.common_currency_yuan, cents / 100.0);
    }
}
