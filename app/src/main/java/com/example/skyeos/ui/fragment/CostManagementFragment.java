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

        if (text(etMonth).isEmpty()) {
            etMonth.setText(YearMonth.now().toString());
        }
        if (text(etRecurringCategory).isEmpty()) {
            etRecurringCategory.setText("subscription");
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
            etCapexUsefulMonths.setText("12");
        }

        View btnSave = view.findViewById(R.id.btn_save_cost_inputs);
        View btnAddRecurring = view.findViewById(R.id.btn_add_recurring);
        View btnAddCapex = view.findViewById(R.id.btn_add_capex);
        bindToggle(tvToggleBaseline, baselineContent, "Monthly Baseline");
        bindToggle(tvToggleRecurring, recurringContent, "Recurring Monthly Costs");
        bindToggle(tvToggleCapex, capexContent, "Capex And Amortization");

        bindData();

        btnSave.setOnClickListener(v -> {
            long idealValue = parseYuanToCents(text(etIdeal));
            long basicValue = parseYuanToCents(text(etMonthlyBasic));
            long fixedValue = parseYuanToCents(text(etMonthlyFixed));
            String month = normalizeMonthOrFallback(text(etMonth));
            graph.useCases.setIdealHourlyRate.execute(idealValue);
            graph.useCases.upsertMonthlyCostBaseline.execute(month, basicValue, fixedValue);
            Snackbar.make(view, "Cost inputs saved", Snackbar.LENGTH_SHORT).show();
            bindData();
        });

        btnAddRecurring.setOnClickListener(v -> {
            graph.useCases.createRecurringCostRule.execute(
                    text(etRecurringName),
                    text(etRecurringCategory),
                    parseYuanToCents(text(etRecurringAmount)),
                    swRecurringNecessary != null && swRecurringNecessary.isChecked(),
                    normalizeMonthOrFallback(text(etRecurringStartMonth)),
                    text(etRecurringEndMonth).isEmpty() ? null : normalizeMonthOrFallback(text(etRecurringEndMonth)),
                    text(etRecurringNote));
            clearRecurringInputs();
            Snackbar.make(view, "Recurring cost rule added", Snackbar.LENGTH_SHORT).show();
            bindData();
        });

        btnAddCapex.setOnClickListener(v -> {
            graph.useCases.createCapexCost.execute(
                    text(etCapexName),
                    normalizeDateOrToday(text(etCapexPurchaseDate)),
                    parseYuanToCents(text(etCapexAmount)),
                    parseIntOrDefault(text(etCapexUsefulMonths), 12),
                    parseIntOrDefault(text(etCapexResidualBps), 0),
                    text(etCapexNote));
            clearCapexInputs();
            Snackbar.make(view, "Capex item added", Snackbar.LENGTH_SHORT).show();
            bindData();
        });
    }

    private void bindToggle(TextView toggle, View content, String title) {
        if (toggle == null || content == null) {
            return;
        }
        toggle.setOnClickListener(v -> {
            boolean expand = content.getVisibility() != View.VISIBLE;
            content.setVisibility(expand ? View.VISIBLE : View.GONE);
            toggle.setText(title + (expand ? "  ▾" : "  ▸"));
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
        tvRateMeta.setText(String.format(
                Locale.US,
                "Current month: income %s, work %s | Previous year: income %s, work %s",
                formatYuan(nullableLong(rates.currentIncomeCents)),
                formatMinutes(rates.currentWorkMinutes),
                formatYuan(nullableLong(rates.previousYearIncomeCents)),
                formatMinutes(rates.previousYearWorkMinutes)));
        tvBaseline.setText(String.format(Locale.US, "%s baseline: Basic %s + Fixed %s",
                month, formatYuan(baseline.basicLivingCents), formatYuan(baseline.fixedSubscriptionCents)));
        tvRecurring.setText(buildRecurringSummary(recurringRules));
        tvCapex.setText(buildCapexSummary(capexCosts));
        etMonth.setText(month);
        etIdeal.setText(toEditableYuan(rates.idealHourlyRateCents));
        etMonthlyBasic.setText(toEditableYuan(baseline.basicLivingCents));
        etMonthlyFixed.setText(toEditableYuan(baseline.fixedSubscriptionCents));
    }

    private static String buildRecurringSummary(List<RecurringCostRuleSummary> recurringRules) {
        StringBuilder sb = new StringBuilder();
        if (recurringRules == null || recurringRules.isEmpty()) {
            sb.append("No recurring cost rules");
        } else {
            int limit = Math.min(3, recurringRules.size());
            for (int i = 0; i < limit; i++) {
                RecurringCostRuleSummary item = recurringRules.get(i);
                sb.append("• ").append(item.name)
                        .append(" ").append(formatYuan(item.monthlyAmountCents)).append("/month")
                        .append(item.necessary ? " | necessary" : " | optional")
                        .append(" | ").append(item.startMonth);
                if (item.endMonth != null && !item.endMonth.isEmpty()) {
                    sb.append(" -> ").append(item.endMonth);
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static String buildCapexSummary(List<CapexCostSummary> capexCosts) {
        StringBuilder sb = new StringBuilder();
        if (capexCosts == null || capexCosts.isEmpty()) {
            sb.append("No capex items");
        } else {
            int limit = Math.min(3, capexCosts.size());
            for (int i = 0; i < limit; i++) {
                CapexCostSummary item = capexCosts.get(i);
                sb.append("• ").append(item.name)
                        .append(" ").append(formatYuan(item.monthlyAmortizedCents)).append("/month")
                        .append(" | ").append(item.amortizationStartMonth)
                        .append(" -> ").append(item.amortizationEndMonth);
                if (i < limit - 1) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString().trim();
    }

    private void clearRecurringInputs() {
        etRecurringName.setText("");
        etRecurringAmount.setText("");
        etRecurringEndMonth.setText("");
        etRecurringNote.setText("");
        if (swRecurringNecessary != null) {
            swRecurringNecessary.setChecked(false);
        }
    }

    private void clearCapexInputs() {
        etCapexName.setText("");
        etCapexAmount.setText("");
        etCapexResidualBps.setText("");
        etCapexNote.setText("");
    }

    private static String formatHourly(long cents) {
        return formatYuan(cents) + "/h";
    }

    private static String formatNullableHourly(Long cents) {
        return cents == null ? "--" : formatHourly(cents);
    }

    private static String formatMinutes(Long minutes) {
        if (minutes == null || minutes <= 0L) {
            return "--";
        }
        return String.format(Locale.US, "%dh %02dm", minutes / 60, minutes % 60);
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

    private static String formatYuan(long cents) {
        if (cents == 0L) {
            return "--";
        }
        if (cents % 100 == 0) {
            return String.format(Locale.US, "¥%,d", cents / 100);
        }
        return String.format(Locale.US, "¥%.2f", cents / 100.0);
    }
}
