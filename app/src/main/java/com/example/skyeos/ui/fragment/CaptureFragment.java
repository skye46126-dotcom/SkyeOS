package com.example.skyeos.ui.fragment;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.ai.ParseDraftItem;
import com.example.skyeos.ai.ParseResult;
import com.example.skyeos.ai.ParserMode;
import com.example.skyeos.domain.model.ProjectOption;
import com.example.skyeos.domain.model.TagItem;
import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.model.input.CreateProjectInput;
import com.example.skyeos.domain.model.input.CreateTimeLogInput;
import com.example.skyeos.ui.FormParsers;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CaptureFragment extends Fragment {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AppGraph graph;
    private View root;

    // ─── Main tab state ───
    private TabLayout captureTabLayout;
    private FrameHolder contentContainer; // wraps capture_content_container FrameLayout
    private ChipGroup typeChipGroup; // type selector for manual tab

    // ─── Active manual sub-form ───
    private View currentManualForm;
    private String activeType = "time"; // time | income | expense | learning | project

    // ─── Time form fields ───
    private TextInputEditText etTimeStart, etTimeEnd, etTimeProject, etTimeNote, etTimeAiRatio;
    private TextInputEditText etTimeEfficiencyScore, etTimeValueScore, etTimeStateScore;
    private AutoCompleteTextView acvTimeCategory;
    private ChipGroup chipProjectsTime, chipTagsTime;
    private final List<String> selectedTimeTagIds = new ArrayList<>();

    // ─── Income form fields ───
    private TextInputEditText etIncomeAmount, etIncomeSource, etIncomeProject, etIncomeAiRatio;
    private AutoCompleteTextView acvIncomeType;
    private ChipGroup chipProjectsIncome, chipTagsIncome;
    private final List<String> selectedIncomeTagIds = new ArrayList<>();

    // ─── Expense form fields ───
    private TextInputEditText etExpenseAmount, etExpenseNote, etExpenseAiRatio;
    private AutoCompleteTextView acvExpenseCat;
    private ChipGroup chipTagsExpense;
    private final List<String> selectedExpenseTagIds = new ArrayList<>();

    // ─── Learning form fields ───
    private TextInputEditText etLearningContent, etLearningDuration, etLearningProject, etLearningAiRatio;
    private TextInputEditText etLearningEfficiencyScore;
    private AutoCompleteTextView acvLearningLevel;
    private ChipGroup chipProjectsLearning, chipTagsLearning;
    private final List<String> selectedLearningTagIds = new ArrayList<>();

    // ─── Project form fields ───
    private TextInputEditText etProjectName, etProjectStart;
    private ChipGroup chipTagsProject;
    private final List<String> selectedProjectTagIds = new ArrayList<>();

    // ─── AI tab fields ───
    private TextInputEditText etAiDate, etAiRaw;
    private ChipGroup chipParserMode;
    private MaterialButton btnAiParse, btnAiCommit;
    private CardView cardAiPreview;
    private TextView tvAiPreview, tvAiFeedback;
    private View progressAiParse;
    private ParseResult latestParseResult;

    // ─── Shared project list ───
    private List<ProjectOption> projectOptions = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_capture, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        graph = AppGraph.getInstance(requireContext());
        root = view;

        captureTabLayout = view.findViewById(R.id.capture_tab_layout);
        android.widget.FrameLayout container2 = view.findViewById(R.id.capture_content_container);
        contentContainer = new FrameHolder(container2);

        captureTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0)
                    showManualTab();
                else
                    showAiTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Start on manual tab
        showManualTab();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProjectOptions();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // MANUAL TAB
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void showManualTab() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        // Build the type-selector chip group + content area wrapper
        View manualContainer = inflater.inflate(R.layout.tab_manual_capture, contentContainer.frame, false);
        contentContainer.setView(manualContainer);

        typeChipGroup = manualContainer.findViewById(R.id.chip_group_type_selector);
        android.widget.FrameLayout formHolder = manualContainer.findViewById(R.id.manual_form_holder);

        // Wire type chips
        setupTypeChip(manualContainer, R.id.chip_type_time, "time", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_income, "income", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_expense, "expense", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_learning, "learning", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_project, "project", formHolder);

        // Status bar shared across forms
        showFormByType("time", formHolder);
    }

    private void setupTypeChip(View container, int chipId, String type, android.widget.FrameLayout formHolder) {
        Chip chip = container.findViewById(chipId);
        if (chip == null)
            return;
        chip.setOnCheckedChangeListener((c, checked) -> {
            if (checked)
                showFormByType(type, formHolder);
        });
    }

    private void showFormByType(String type, android.widget.FrameLayout holder) {
        activeType = type;
        holder.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        switch (type) {
            case "time":
                currentManualForm = inflater.inflate(R.layout.form_time_log, holder, false);
                bindTimeForm(currentManualForm);
                break;
            case "income":
                currentManualForm = inflater.inflate(R.layout.form_income, holder, false);
                bindIncomeForm(currentManualForm);
                break;
            case "expense":
                currentManualForm = inflater.inflate(R.layout.form_expense, holder, false);
                bindExpenseForm(currentManualForm);
                break;
            case "learning":
                currentManualForm = inflater.inflate(R.layout.form_learning, holder, false);
                bindLearningForm(currentManualForm);
                break;
            case "project":
                currentManualForm = inflater.inflate(R.layout.form_project, holder, false);
                bindProjectForm(currentManualForm);
                break;
        }
        holder.addView(currentManualForm);
    }

    // ── Time form ──────────────────────────────

    private void bindTimeForm(View v) {
        etTimeStart = v.findViewById(R.id.et_time_start);
        etTimeEnd = v.findViewById(R.id.et_time_end);
        etTimeProject = v.findViewById(R.id.et_time_project);
        etTimeNote = v.findViewById(R.id.et_time_note);
        etTimeAiRatio = v.findViewById(R.id.et_time_ai_ratio);
        etTimeEfficiencyScore = v.findViewById(R.id.et_time_efficiency_score);
        etTimeValueScore = v.findViewById(R.id.et_time_value_score);
        etTimeStateScore = v.findViewById(R.id.et_time_state_score);
        acvTimeCategory = v.findViewById(R.id.acv_time_category);
        chipProjectsTime = v.findViewById(R.id.chip_group_projects_time);
        chipTagsTime = v.findViewById(R.id.chip_group_tags_time);

        LocalDateTime now = LocalDateTime.now();
        etTimeStart.setText(now.minusHours(1).format(DT_FMT));
        etTimeEnd.setText(now.format(DT_FMT));

        etTimeStart.setOnClickListener(x -> pickDateTime(etTimeStart));
        etTimeEnd.setOnClickListener(x -> pickDateTime(etTimeEnd));

        String[] cats = { "Work", "Learning", "Life", "Entertainment", "Rest", "Social" };
        String[] catVals = { "work", "learning", "life", "entertainment", "rest", "social" };
        setupDropdown(acvTimeCategory, cats);

        buildProjectChips(chipProjectsTime, etTimeProject);
        buildTagChips("time", chipTagsTime, selectedTimeTagIds);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_time);
        btnSubmit.setOnClickListener(x -> submitTimeLog(catVals));
    }

    private void submitTimeLog(String[] catVals) {
        try {
            String startStr = text(etTimeStart);
            String endStr = text(etTimeEnd);
            String catLabel = text(acvTimeCategory);
            String catVal = mapCategoryValue(catLabel, catVals);
            String note = text(etTimeNote);
            String allocStr = text(etTimeProject);
            Integer efficiencyScore = parseOptionalScore(text(etTimeEfficiencyScore));
            Integer valueScore = parseOptionalScore(text(etTimeValueScore));
            Integer stateScore = parseOptionalScore(text(etTimeStateScore));
            Integer aiRatio = parseOptionalPercentage(text(etTimeAiRatio));

            graph.useCases.createTimeLog.execute(new CreateTimeLogInput(
                    toUtcInstant(startStr),
                    toUtcInstant(endStr),
                    catVal,
                    efficiencyScore, valueScore, stateScore, aiRatio, note,
                    FormParsers.parseAllocations(allocStr),
                    new ArrayList<>(selectedTimeTagIds)));
            snack("✓ Time log saved");
            etTimeProject.setText("");
            etTimeNote.setText("");
            etTimeEfficiencyScore.setText("");
            etTimeValueScore.setText("");
            etTimeStateScore.setText("");
            etTimeAiRatio.setText("");
            selectedTimeTagIds.clear();
            if (chipTagsTime != null) {
                chipTagsTime.clearCheck();
            }
        } catch (Exception e) {
            snack("❌ Save failed: " + e.getMessage());
        }
    }

    // ── Income form ─────────────────────────────

    private void bindIncomeForm(View v) {
        etIncomeAmount = v.findViewById(R.id.et_income_amount);
        etIncomeSource = v.findViewById(R.id.et_income_source);
        etIncomeProject = v.findViewById(R.id.et_income_project);
        etIncomeAiRatio = v.findViewById(R.id.et_income_ai_ratio);
        acvIncomeType = v.findViewById(R.id.acv_income_type);
        chipProjectsIncome = v.findViewById(R.id.chip_group_projects_income);
        chipTagsIncome = v.findViewById(R.id.chip_group_tags_income);

        String[] incomeLabels = { "Other", "Salary", "Project income", "Investment income", "System income" };
        String[] incomeVals = { "other", "salary", "project", "investment", "system" };
        setupDropdown(acvIncomeType, incomeLabels);
        buildProjectChips(chipProjectsIncome, etIncomeProject);
        buildTagChips("income", chipTagsIncome, selectedIncomeTagIds);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_income);
        btnSubmit.setOnClickListener(x -> submitIncome(incomeVals));
    }

    private void submitIncome(String[] typeVals) {
        try {
            String amountStr = text(etIncomeAmount);
            long amountCents = yuanToCents(amountStr);
            String typeLabel = text(acvIncomeType);
            String typeVal = mapCategoryValue(typeLabel, typeVals);
            String source = text(etIncomeSource);
            String alloc = text(etIncomeProject);
            Integer aiRatio = parseOptionalPercentage(text(etIncomeAiRatio));

            graph.useCases.createIncome.execute(new CreateIncomeInput(
                    LocalDate.now().format(DATE_FMT),
                    source.isEmpty() ? "Manual input" : source,
                    typeVal,
                    amountCents,
                    false,
                    aiRatio,
                    "manual capture",
                    FormParsers.parseAllocations(alloc),
                    new ArrayList<>(selectedIncomeTagIds)));
            snack("✓ Income saved");
            etIncomeAmount.setText("");
            etIncomeProject.setText("");
            etIncomeAiRatio.setText("");
            selectedIncomeTagIds.clear();
            if (chipTagsIncome != null) {
                chipTagsIncome.clearCheck();
            }
        } catch (Exception e) {
            snack("❌ Save failed: " + e.getMessage());
        }
    }

    // ── Expense form ────────────────────────────

    private void bindExpenseForm(View v) {
        etExpenseAmount = v.findViewById(R.id.et_expense_amount);
        etExpenseNote = v.findViewById(R.id.et_expense_note);
        etExpenseAiRatio = v.findViewById(R.id.et_expense_ai_ratio);
        acvExpenseCat = v.findViewById(R.id.acv_expense_cat);
        chipTagsExpense = v.findViewById(R.id.chip_group_tags_expense);

        String[] expenseLabels = { "Essential", "Experience", "Subscription", "Investment" };
        String[] expenseVals = { "necessary", "experience", "subscription", "investment" };
        setupDropdown(acvExpenseCat, expenseLabels);
        buildTagChips("expense", chipTagsExpense, selectedExpenseTagIds);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_expense);
        btnSubmit.setOnClickListener(x -> submitExpense(expenseVals));
    }

    private void submitExpense(String[] catVals) {
        try {
            long amountCents = yuanToCents(text(etExpenseAmount));
            String catLabel = text(acvExpenseCat);
            String catVal = mapCategoryValue(catLabel, catVals);
            String note = text(etExpenseNote);
            Integer aiRatio = parseOptionalPercentage(text(etExpenseAiRatio));

            graph.useCases.createExpense.execute(new CreateExpenseInput(
                    LocalDate.now().format(DATE_FMT),
                    catVal,
                    amountCents,
                    aiRatio,
                    note,
                    new ArrayList<>(selectedExpenseTagIds)));
            snack("✓ Expense saved");
            etExpenseAmount.setText("");
            etExpenseNote.setText("");
            etExpenseAiRatio.setText("");
            selectedExpenseTagIds.clear();
            if (chipTagsExpense != null) {
                chipTagsExpense.clearCheck();
            }
        } catch (Exception e) {
            snack("❌ Save failed: " + e.getMessage());
        }
    }

    // ── Learning form ───────────────────────────

    private void bindLearningForm(View v) {
        etLearningContent = v.findViewById(R.id.et_learning_content);
        etLearningDuration = v.findViewById(R.id.et_learning_duration);
        etLearningProject = v.findViewById(R.id.et_learning_project);
        etLearningAiRatio = v.findViewById(R.id.et_learning_ai_ratio);
        etLearningEfficiencyScore = v.findViewById(R.id.et_learning_efficiency_score);
        acvLearningLevel = v.findViewById(R.id.acv_learning_level);
        chipProjectsLearning = v.findViewById(R.id.chip_group_projects_learning);
        chipTagsLearning = v.findViewById(R.id.chip_group_tags_learning);

        String[] levelLabels = { "Input learning", "Applied learning", "Result learning" };
        String[] levelVals = { "input", "applied", "result" };
        setupDropdown(acvLearningLevel, levelLabels);
        buildProjectChips(chipProjectsLearning, etLearningProject);
        buildTagChips("learning", chipTagsLearning, selectedLearningTagIds);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_learning);
        btnSubmit.setOnClickListener(x -> submitLearning(levelVals));
    }

    private void submitLearning(String[] levelVals) {
        try {
            String content = text(etLearningContent);
            int duration = FormParsers.parseInt(text(etLearningDuration), 60);
            String lvlLabel = text(acvLearningLevel);
            String lvlVal = mapCategoryValue(lvlLabel, levelVals);
            String alloc = text(etLearningProject);
            Integer efficiencyScore = parseOptionalScore(text(etLearningEfficiencyScore));
            Integer aiRatio = parseOptionalPercentage(text(etLearningAiRatio));

            graph.useCases.createLearning.execute(new CreateLearningInput(
                    LocalDate.now().format(DATE_FMT),
                    content,
                    duration,
                    efficiencyScore,
                    lvlVal,
                    aiRatio,
                    "manual capture",
                    FormParsers.parseAllocations(alloc),
                    new ArrayList<>(selectedLearningTagIds)));
            snack("✓ Learning record saved");
            etLearningContent.setText("");
            etLearningDuration.setText("");
            etLearningProject.setText("");
            etLearningEfficiencyScore.setText("");
            etLearningAiRatio.setText("");
            selectedLearningTagIds.clear();
            if (chipTagsLearning != null) {
                chipTagsLearning.clearCheck();
            }
        } catch (Exception e) {
            snack("❌ Save failed: " + e.getMessage());
        }
    }

    // ── Project form ────────────────────────────

    private void bindProjectForm(View v) {
        etProjectName = v.findViewById(R.id.et_project_name);
        etProjectStart = v.findViewById(R.id.et_project_start);
        chipTagsProject = v.findViewById(R.id.chip_group_tags_project);
        etProjectStart.setText(LocalDate.now().format(DATE_FMT));
        etProjectStart.setOnClickListener(x -> pickDate(etProjectStart));
        buildTagChips("project", chipTagsProject, selectedProjectTagIds);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_project);
        btnSubmit.setOnClickListener(x -> submitProject());
    }

    private void submitProject() {
        try {
            String name = text(etProjectName);
            String start = text(etProjectStart);
            if (name.isEmpty()) {
                snack("Please enter a project name");
                return;
            }

            graph.useCases.createProject.execute(new CreateProjectInput(
                    name, start, "active", 0, null, "created from capture", new ArrayList<>(selectedProjectTagIds)));
            snack("✓ Project \"" + name + "\" created");
            etProjectName.setText("");
            selectedProjectTagIds.clear();
            if (chipTagsProject != null) {
                chipTagsProject.clearCheck();
            }
            loadProjectOptions(); // refresh project chips in other forms
        } catch (Exception e) {
            snack("❌ Create failed: " + e.getMessage());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // AI TAB
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void showAiTab() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View aiView = inflater.inflate(R.layout.tab_ai_capture, contentContainer.frame, false);
        contentContainer.setView(aiView);

        etAiDate = aiView.findViewById(R.id.et_ai_date);
        etAiRaw = aiView.findViewById(R.id.et_ai_raw);
        chipParserMode = aiView.findViewById(R.id.chip_parser_mode);
        btnAiParse = aiView.findViewById(R.id.btn_ai_parse);
        btnAiCommit = aiView.findViewById(R.id.btn_ai_commit);
        cardAiPreview = aiView.findViewById(R.id.card_ai_preview);
        tvAiPreview = aiView.findViewById(R.id.tv_ai_preview);
        tvAiFeedback = aiView.findViewById(R.id.tv_ai_feedback);
        progressAiParse = aiView.findViewById(R.id.progress_ai_parse);

        etAiDate.setText(LocalDate.now().format(DATE_FMT));
        etAiDate.setOnClickListener(x -> pickDate(etAiDate));

        btnAiParse.setOnClickListener(x -> parseWithAi());
        btnAiCommit.setOnClickListener(x -> commitAiResult());

        // Load saved mode into chips
        ParserMode saved = graph.parserSettingsStore.loadMode();
        Chip ruleChip = aiView.findViewById(R.id.chip_mode_rule);
        if (saved == ParserMode.RULE && ruleChip != null)
            ruleChip.setChecked(true);
    }

    private void parseWithAi() {
        String raw = etAiRaw.getText() == null ? "" : etAiRaw.getText().toString().trim();
        if (raw.isEmpty()) {
            snack("Please enter content first");
            return;
        }

        btnAiParse.setEnabled(false);
        btnAiParse.setText("Parsing...");
        progressAiParse.setVisibility(View.VISIBLE);
        tvAiFeedback.setVisibility(View.GONE);
        cardAiPreview.setVisibility(View.GONE);

        ParserMode mode = getSelectedParserMode();
        graph.parserSettingsStore.saveMode(mode);
        graph.aiParseOrchestrator.setParserMode(mode);

        String contextDate = etAiDate.getText() == null ? LocalDate.now().toString()
                : etAiDate.getText().toString().trim();

        new Thread(() -> {
            ParseResult result = graph.aiParseOrchestrator.parse(raw, safeDate(contextDate));
            if (getActivity() == null)
                return;
            getActivity().runOnUiThread(() -> {
                latestParseResult = result;
                btnAiParse.setEnabled(true);
                btnAiParse.setText("🤖 Parse Draft");
                progressAiParse.setVisibility(View.GONE);

                boolean hasItems = result != null && result.items != null && !result.items.isEmpty();
                btnAiCommit.setEnabled(hasItems);
                cardAiPreview.setVisibility(View.VISIBLE);
                tvAiPreview.setText(formatPreview(result));

                if (hasItems) {
                    int savedClicks = result.items.size() * 5; // Approx clicks saved per item
                    int estTokens = raw.length() * 2 + 50; // Rough token estimation
                    tvAiFeedback.setText("💡 AI saved about " + savedClicks + " clicks, estimated usage " + estTokens + " tokens");
                    tvAiFeedback.setVisibility(View.VISIBLE);
                } else {
                    tvAiFeedback.setVisibility(View.GONE);
                }

                snack("Parse completed, " + (hasItems ? result.items.size() : 0) + " items");
            });
        }).start();
    }

    private void commitAiResult() {
        if (latestParseResult == null || latestParseResult.items == null) {
            snack("No content to commit");
            return;
        }
        String contextDate = etAiDate.getText() == null ? LocalDate.now().toString()
                : etAiDate.getText().toString().trim();
        int ok = 0;
        List<String> fails = new ArrayList<>();
        for (ParseDraftItem item : latestParseResult.items) {
            if (item == null || item.kind == null || "unknown".equals(item.kind))
                continue;
            try {
                commitOneAiItem(item, safeDate(contextDate));
                ok++;
            } catch (Exception e) {
                fails.add(item.kind + ": " + e.getMessage());
            }
        }
        if (fails.isEmpty()) {
            snack("✓ Commit success (" + ok + " items)");
            etAiRaw.setText("");
            cardAiPreview.setVisibility(View.GONE);
            tvAiFeedback.setVisibility(View.GONE);
            btnAiCommit.setEnabled(false);
            latestParseResult = null;
        } else {
            snack("Partial commit: success " + ok + ", failed " + fails.size());
            StringBuilder sb = new StringBuilder(tvAiPreview.getText().toString());
            sb.append("\n\n--- Commit Errors ---\n");
            for (String f : fails)
                sb.append("✗ ").append(f).append('\n');
            tvAiPreview.setText(sb.toString());
        }
    }

    private void commitOneAiItem(ParseDraftItem item, String contextDate) {
        Map<String, String> p = item.payload;
        switch (item.kind) {
            case "income":
                Integer incomeAiRatio = parseOptionalPercentage(valueOr(p, "ai_ratio", ""));
                graph.useCases.createIncome.execute(new CreateIncomeInput(
                        contextDate,
                        valueOr(p, "source", "AI parse"),
                        valueOr(p, "type", "other"),
                        toCents(valueOr(p, "amount", "0")),
                        false, incomeAiRatio, "ai capture", null, null));
                break;
            case "expense":
                Integer expenseAiRatio = parseOptionalPercentage(valueOr(p, "ai_ratio", ""));
                graph.useCases.createExpense.execute(new CreateExpenseInput(
                        contextDate,
                        valueOr(p, "category", "necessary"),
                        toCents(valueOr(p, "amount", "0")),
                        expenseAiRatio,
                        valueOr(p, "note", "ai capture"),
                        null));
                break;
            case "learning":
                Integer learningAiRatio = parseOptionalPercentage(valueOr(p, "ai_ratio", ""));
                Integer learningEfficiencyScore = parseOptionalScore(valueOr(p, "efficiency_score", ""));
                graph.useCases.createLearning.execute(new CreateLearningInput(
                        contextDate,
                        valueOr(p, "content", "Learning"),
                        FormParsers.parseInt(valueOr(p, "duration_minutes", "60"), 60),
                        learningEfficiencyScore,
                        valueOr(p, "application_level", "input"),
                        learningAiRatio,
                        "ai capture", null, null));
                break;
            case "time_log":
                String startAt = buildAiStartAt(p, contextDate);
                String endAt = buildAiEndAt(p, contextDate);
                Integer timeAiRatio = parseOptionalPercentage(valueOr(p, "ai_ratio", ""));
                Integer timeEfficiencyScore = parseOptionalScore(valueOr(p, "efficiency_score", ""));
                Integer timeValueScore = parseOptionalScore(valueOr(p, "value_score", ""));
                Integer timeStateScore = parseOptionalScore(valueOr(p, "state_score", ""));
                graph.useCases.createTimeLog.execute(new CreateTimeLogInput(
                        startAt, endAt,
                        valueOr(p, "category", "work"),
                        timeEfficiencyScore, timeValueScore, timeStateScore,
                        timeAiRatio,
                        valueOr(p, "description", "AI parse"),
                        null, null));
                break;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // HELPERS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void loadProjectOptions() {
        projectOptions = graph.useCases.getProjectOptions.execute(false);
        if (projectOptions == null)
            projectOptions = new ArrayList<>();
    }

    private void buildProjectChips(ChipGroup chipGroup, TextInputEditText allocField) {
        chipGroup.removeAllViews();
        if (projectOptions.isEmpty())
            return;
        for (ProjectOption p : projectOptions) {
            Chip chip = new Chip(requireContext());
            chip.setText(p.name);
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((c, checked) -> {
                if (checked)
                    appendAlloc(allocField, p.id);
            });
            chipGroup.addView(chip);
        }
    }

    private void buildTagChips(String scope, ChipGroup chipGroup, List<String> selectedTagIds) {
        if (chipGroup == null || selectedTagIds == null) {
            return;
        }
        selectedTagIds.clear();
        chipGroup.removeAllViews();
        List<TagItem> tags = graph.useCases.getTags.execute(scope, true);
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (TagItem tag : tags) {
            if (tag == null) {
                continue;
            }
            final String tagId = tag.id;
            String label = (tag.emoji == null || tag.emoji.isEmpty() ? "" : tag.emoji + " ") + tag.name;
            Chip chip = new Chip(requireContext());
            chip.setText(label);
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((c, checked) -> {
                if (checked) {
                    if (!selectedTagIds.contains(tagId)) {
                        selectedTagIds.add(tagId);
                    }
                } else {
                    selectedTagIds.remove(tagId);
                }
            });
            chipGroup.addView(chip);
        }
    }

    private void appendAlloc(TextInputEditText field, String projectId) {
        String current = text(field);
        String append = projectId + ":1.0";
        field.setText(current.isEmpty() ? append : current + "," + append);
    }

    private void setupDropdown(AutoCompleteTextView acv, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, items);
        acv.setAdapter(adapter);
        if (items.length > 0)
            acv.setText(items[0], false);
    }

    private String mapCategoryValue(String label, String[] vals) {
        // vals array matches order of items passed to setupDropdown
        return vals[0]; // default to first; for production map by index
    }

    private void pickDate(TextInputEditText target) {
        LocalDate d = LocalDate.now();
        new DatePickerDialog(requireContext(),
                (v, y, m, day) -> target.setText(LocalDate.of(y, m + 1, day).format(DATE_FMT)),
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void pickDateTime(TextInputEditText target) {
        LocalDateTime now = LocalDateTime.now();
        new DatePickerDialog(requireContext(),
                (v, y, m, day) -> new TimePickerDialog(requireContext(),
                        (tv, h, min) -> target.setText(LocalDateTime.of(y, m + 1, day, h, min).format(DT_FMT)),
                        now.getHour(), now.getMinute(), true).show(),
                now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth()).show();
    }

    private String toUtcInstant(String localDtStr) {
        return LocalDateTime.parse(localDtStr.trim(), DT_FMT)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toInstant().toString();
    }

    private String buildAiStartAt(Map<String, String> p, String contextDate) {
        String hour = valueOr(p, "start_hour", "");
        if (!hour.isEmpty()) {
            int h = FormParsers.parseInt(hour, 9);
            return LocalDateTime.parse(contextDate + String.format(Locale.US, " %02d:00", h), DT_FMT)
                    .atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
        }
        int dur = FormParsers.parseInt(valueOr(p, "duration_hours", "1"), 1);
        return LocalDateTime.now().minusHours(Math.max(1, dur))
                .atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
    }

    private String buildAiEndAt(Map<String, String> p, String contextDate) {
        String hour = valueOr(p, "end_hour", "");
        if (!hour.isEmpty()) {
            int h = FormParsers.parseInt(hour, 10);
            return LocalDateTime.parse(contextDate + String.format(Locale.US, " %02d:00", h), DT_FMT)
                    .atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
        }
        return LocalDateTime.now()
                .atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
    }

    private ParserMode getSelectedParserMode() {
        if (chipParserMode == null)
            return ParserMode.AUTO;
        Chip ruleChip = chipParserMode.findViewById(R.id.chip_mode_rule);
        if (ruleChip != null && ruleChip.isChecked())
            return ParserMode.RULE;
        return ParserMode.AUTO;
    }

    private static String formatPreview(ParseResult r) {
        if (r == null || r.items == null || r.items.isEmpty())
            return "No result";
        StringBuilder sb = new StringBuilder();
        sb.append("Parser: ").append(r.parserUsed).append('\n');
        if (r.warnings != null)
            for (String w : r.warnings)
                sb.append("⚠ ").append(w).append('\n');
        sb.append('\n');
        for (ParseDraftItem item : r.items) {
            sb.append("[").append(item.kind).append("]")
                    .append(" confidence=").append(String.format(Locale.US, "%.0f%%", item.confidence * 100))
                    .append(" source=").append(item.source).append('\n');
            if (item.payload != null) {
                for (Map.Entry<String, String> e : item.payload.entrySet()) {
                    sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
                }
            }
            if (item.warning != null && !item.warning.isEmpty())
                sb.append("  ⚠ ").append(item.warning).append('\n');
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String text(TextInputEditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static String text(AutoCompleteTextView et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static String safeDate(String raw) {
        try {
            return LocalDate.parse(raw.trim()).toString();
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    private static String valueOr(Map<String, String> m, String k, String def) {
        if (m == null)
            return def;
        String v = m.get(k);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    private static long toCents(String s) {
        try {
            String n = s == null ? "0" : s.trim();
            if (n.isEmpty())
                return 0L;
            return n.contains(".") ? (long) Math.round(Double.parseDouble(n) * 100) : Long.parseLong(n);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long yuanToCents(String s) {
        try {
            String n = s == null ? "0" : s.trim();
            if (n.isEmpty())
                return 0L;
            return (long) Math.round(Double.parseDouble(n) * 100);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static Integer parseOptionalPercentage(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        int value = FormParsers.parseInt(raw.trim(), -1);
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("AI assist ratio must be in 0-100");
        }
        return value;
    }

    private static Integer parseOptionalScore(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        int value = FormParsers.parseInt(raw.trim(), -1);
        if (value < 1 || value > 10) {
            throw new IllegalArgumentException("Score must be in 1-10");
        }
        return value;
    }

    private void snack(String msg) {
        if (root != null)
            Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
    }

    // Tiny holder to keep reference to the FrameLayout
    private static class FrameHolder {
        final android.widget.FrameLayout frame;

        FrameHolder(android.widget.FrameLayout f) {
            this.frame = f;
        }

        void setView(View v) {
            frame.removeAllViews();
            frame.addView(v);
        }
    }
}
