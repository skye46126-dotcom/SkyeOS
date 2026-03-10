package com.example.skyeos.ui.fragment;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.ai.ParseDraftItem;
import com.example.skyeos.ai.ParseResult;
import com.example.skyeos.ai.ParserContext;
import com.example.skyeos.ai.ParserMode;
import com.example.skyeos.domain.model.ProjectAllocation;
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

import java.time.Duration;
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
    private static final String ARG_INITIAL_TYPE = "initial_type";

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
    private String activeType = "work"; // work | time | income | expense | learning | project

    // ─── Work form fields ───
    private TextInputEditText etWorkStart, etWorkEnd, etWorkNote, etWorkAiRatio;
    private TextInputEditText etWorkEfficiencyScore, etWorkValueScore, etWorkStateScore;
    private AutoCompleteTextView acvWorkProject;
    private MultiAutoCompleteTextView acvWorkTags;

    // ─── Non-work time form fields ───
    private TextInputEditText etTimeStart, etTimeEnd, etTimeNote;
    private AutoCompleteTextView acvTimeCategory, acvTimeProject;
    private MultiAutoCompleteTextView acvTimeTags;

    // ─── Income form fields ───
    private TextInputEditText etIncomeAmount, etIncomeSource;
    private AutoCompleteTextView acvIncomeType, acvIncomeProject;
    private MultiAutoCompleteTextView acvIncomeTags;

    // ─── Expense form fields ───
    private TextInputEditText etExpenseAmount, etExpenseNote;
    private AutoCompleteTextView acvExpenseCat, acvExpenseProject;
    private MultiAutoCompleteTextView acvExpenseTags;

    // ─── Learning form fields ───
    private TextInputEditText etLearningDate, etLearningStart, etLearningEnd, etLearningContent, etLearningDuration,
            etLearningAiRatio, etLearningNote;
    private TextInputEditText etLearningEfficiencyScore;
    private AutoCompleteTextView acvLearningLevel, acvLearningProject;
    private MultiAutoCompleteTextView acvLearningTags;

    // ─── Project form fields ───
    private TextInputEditText etProjectName, etProjectStart;
    private MultiAutoCompleteTextView acvProjectTags;

    // ─── AI tab fields ───
    private TextInputEditText etAiDate, etAiRaw;
    private ChipGroup chipParserMode;
    private MaterialButton btnAiParse, btnAiCommit;
    private CardView cardAiPreview;
    private TextView tvAiPreview, tvAiFeedback, tvAiPreviewLabel;
    private LinearLayout llAiDraftContainer;
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

    public static CaptureFragment newManualWithType(String type) {
        CaptureFragment fragment = new CaptureFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_TYPE, type);
        fragment.setArguments(args);
        return fragment;
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
        setupTypeChip(manualContainer, R.id.chip_type_work, "work", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_time, "time", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_income, "income", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_expense, "expense", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_learning, "learning", formHolder);
        setupTypeChip(manualContainer, R.id.chip_type_project, "project", formHolder);

        // Status bar shared across forms
        showFormByType(resolveInitialType(), formHolder);
    }

    private String resolveInitialType() {
        Bundle args = getArguments();
        String input = args == null ? null : args.getString(ARG_INITIAL_TYPE);
        if (input == null) {
            return "work";
        }
        switch (input.trim().toLowerCase(Locale.US)) {
            case "work":
            case "income":
            case "expense":
            case "learning":
            case "project":
            case "time":
                return input.trim().toLowerCase(Locale.US);
            default:
                return "work";
        }
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
            case "work":
                currentManualForm = inflater.inflate(R.layout.form_work_log, holder, false);
                bindWorkForm(currentManualForm);
                break;
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

    // ── Work form ──────────────────────────────

    private void bindWorkForm(View v) {
        etWorkStart = v.findViewById(R.id.et_work_start);
        etWorkEnd = v.findViewById(R.id.et_work_end);
        etWorkNote = v.findViewById(R.id.et_work_note);
        etWorkAiRatio = v.findViewById(R.id.et_work_ai_ratio);
        etWorkEfficiencyScore = v.findViewById(R.id.et_work_efficiency_score);
        etWorkValueScore = v.findViewById(R.id.et_work_value_score);
        etWorkStateScore = v.findViewById(R.id.et_work_state_score);
        acvWorkProject = v.findViewById(R.id.et_work_project);
        acvWorkTags = v.findViewById(R.id.acv_work_tags);

        LocalDateTime now = LocalDateTime.now();
        etWorkStart.setText(now.minusHours(1).format(DT_FMT));
        etWorkEnd.setText(now.format(DT_FMT));

        etWorkStart.setOnClickListener(x -> pickDateTime(etWorkStart));
        etWorkEnd.setOnClickListener(x -> pickDateTime(etWorkEnd));
        setupProjectDropdown(acvWorkProject);
        setupTagDropdown(acvWorkTags, "time", "work");

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_work);
        btnSubmit.setOnClickListener(x -> submitWorkLog());
    }

    private void submitWorkLog() {
        try {
            String note = text(etWorkNote);
            Integer efficiencyScore = parseOptionalScore(text(etWorkEfficiencyScore));
            Integer valueScore = parseOptionalScore(text(etWorkValueScore));
            Integer stateScore = parseOptionalScore(text(etWorkStateScore));
            Integer aiRatio = parseOptionalPercentage(text(etWorkAiRatio));
            validateWorkLearningRequired("work", valueScore, stateScore, aiRatio);

            graph.useCases.createTimeLog.execute(new CreateTimeLogInput(
                    toUtcInstant(text(etWorkStart)),
                    toUtcInstant(text(etWorkEnd)),
                    "work",
                    efficiencyScore, valueScore, stateScore, aiRatio, note,
                    resolveProjectAllocationsFromDropdown(text(acvWorkProject)),
                    resolveTagIdsFromDropdown("time", text(acvWorkTags), "work")));
            snack(getString(R.string.capture_work_saved));
            acvWorkProject.setText("");
            etWorkNote.setText("");
            etWorkEfficiencyScore.setText("");
            etWorkValueScore.setText("");
            etWorkStateScore.setText("");
            etWorkAiRatio.setText("");
            if (acvWorkTags != null) {
                acvWorkTags.setText("");
            }
        } catch (Exception e) {
            snack(getString(R.string.capture_save_failed, safeErrorMessage(e)));
        }
    }

    // ── Non-work time form ─────────────────────

    private void bindTimeForm(View v) {
        etTimeStart = v.findViewById(R.id.et_time_start);
        etTimeEnd = v.findViewById(R.id.et_time_end);
        etTimeNote = v.findViewById(R.id.et_time_note);
        acvTimeCategory = v.findViewById(R.id.acv_time_category);
        acvTimeProject = v.findViewById(R.id.et_time_project);
        acvTimeTags = v.findViewById(R.id.acv_time_tags);

        LocalDateTime now = LocalDateTime.now();
        etTimeStart.setText(now.minusHours(1).format(DT_FMT));
        etTimeEnd.setText(now.format(DT_FMT));

        etTimeStart.setOnClickListener(x -> pickDateTime(etTimeStart));
        etTimeEnd.setOnClickListener(x -> pickDateTime(etTimeEnd));

        String[] cats = {
                getString(R.string.capture_time_category_life),
                getString(R.string.capture_time_category_entertainment),
                getString(R.string.capture_time_category_rest),
                getString(R.string.capture_time_category_social)
        };
        String[] catVals = { "life", "entertainment", "rest", "social" };
        setupDropdown(acvTimeCategory, cats);
        acvTimeCategory.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String normalized = mapCategoryValue(
                        s == null ? "" : s.toString(),
                        cats,
                        catVals);
                setupTagDropdown(acvTimeTags, "time", normalized);
            }
        });

        setupProjectDropdown(acvTimeProject);
        String initialCategory = mapCategoryValue(text(acvTimeCategory), cats, catVals);
        setupTagDropdown(acvTimeTags, "time", initialCategory);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_time);
        btnSubmit.setOnClickListener(x -> submitTimeLog(cats, catVals));
    }

    private void submitTimeLog(String[] catLabels, String[] catVals) {
        try {
            String catLabel = text(acvTimeCategory);
            String catVal = mapCategoryValue(catLabel, catLabels, catVals);
            graph.useCases.createTimeLog.execute(new CreateTimeLogInput(
                    toUtcInstant(text(etTimeStart)),
                    toUtcInstant(text(etTimeEnd)),
                    catVal,
                    null, null, null, null, text(etTimeNote),
                    resolveProjectAllocationsFromDropdown(text(acvTimeProject)),
                    resolveTagIdsFromDropdown("time", text(acvTimeTags), catVal)));
            snack(getString(R.string.capture_time_saved));
            acvTimeProject.setText("");
            etTimeNote.setText("");
            if (acvTimeTags != null) {
                acvTimeTags.setText("");
            }
        } catch (Exception e) {
            snack(getString(R.string.capture_save_failed, safeErrorMessage(e)));
        }
    }

    // ── Income form ─────────────────────────────

    private void bindIncomeForm(View v) {
        etIncomeAmount = v.findViewById(R.id.et_income_amount);
        etIncomeSource = v.findViewById(R.id.et_income_source);
        acvIncomeType = v.findViewById(R.id.acv_income_type);
        acvIncomeProject = v.findViewById(R.id.et_income_project);
        acvIncomeTags = v.findViewById(R.id.acv_income_tags);

        String[] incomeLabels = {
                getString(R.string.capture_income_type_other),
                getString(R.string.capture_income_type_salary),
                getString(R.string.capture_income_type_project),
                getString(R.string.capture_income_type_investment),
                getString(R.string.capture_income_type_system)
        };
        String[] incomeVals = { "other", "salary", "project", "investment", "system" };
        setupDropdown(acvIncomeType, incomeLabels);
        setupProjectDropdown(acvIncomeProject);
        setupTagDropdown(acvIncomeTags, "income", null);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_income);
        btnSubmit.setOnClickListener(x -> submitIncome(incomeLabels, incomeVals));
    }

    private void submitIncome(String[] typeLabels, String[] typeVals) {
        try {
            String amountStr = text(etIncomeAmount);
            long amountCents = yuanToCents(amountStr);
            String typeLabel = text(acvIncomeType);
            String typeVal = mapCategoryValue(typeLabel, typeLabels, typeVals);
            String source = text(etIncomeSource);

            graph.useCases.createIncome.execute(new CreateIncomeInput(
                    LocalDate.now().format(DATE_FMT),
                    source.isEmpty() ? getString(R.string.capture_source_manual_input) : source,
                    typeVal,
                    amountCents,
                    false,
                    null,
                    getString(R.string.capture_note_manual_capture),
                    resolveProjectAllocationsFromDropdown(text(acvIncomeProject)),
                    resolveTagIdsFromDropdown("income", text(acvIncomeTags), null)));
            snack(getString(R.string.capture_income_saved));
            etIncomeAmount.setText("");
            acvIncomeProject.setText("");
            if (acvIncomeTags != null) {
                acvIncomeTags.setText("");
            }
        } catch (Exception e) {
            snack(getString(R.string.capture_save_failed, safeErrorMessage(e)));
        }
    }

    // ── Expense form ────────────────────────────

    private void bindExpenseForm(View v) {
        etExpenseAmount = v.findViewById(R.id.et_expense_amount);
        etExpenseNote = v.findViewById(R.id.et_expense_note);
        acvExpenseCat = v.findViewById(R.id.acv_expense_cat);
        acvExpenseProject = v.findViewById(R.id.et_expense_project);
        acvExpenseTags = v.findViewById(R.id.acv_expense_tags);

        String[] expenseLabels = {
                getString(R.string.capture_expense_category_essential),
                getString(R.string.capture_expense_category_experience),
                getString(R.string.capture_expense_category_subscription),
                getString(R.string.capture_expense_category_investment)
        };
        String[] expenseVals = { "necessary", "experience", "subscription", "investment" };
        setupDropdown(acvExpenseCat, expenseLabels);
        setupProjectDropdown(acvExpenseProject);
        setupTagDropdown(acvExpenseTags, "expense", null);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_expense);
        btnSubmit.setOnClickListener(x -> submitExpense(expenseLabels, expenseVals));
    }

    private void submitExpense(String[] catLabels, String[] catVals) {
        try {
            long amountCents = yuanToCents(text(etExpenseAmount));
            if (amountCents <= 0) {
                throw new IllegalArgumentException(getString(R.string.capture_error_expense_amount_gt_zero));
            }
            String catLabel = text(acvExpenseCat);
            String catVal = mapCategoryValue(catLabel, catLabels, catVals);
            String note = text(etExpenseNote);

            graph.useCases.createExpense.execute(new CreateExpenseInput(
                    LocalDate.now().format(DATE_FMT),
                    catVal,
                    amountCents,
                    null,
                    note,
                    resolveProjectAllocationsFromDropdown(text(acvExpenseProject)),
                    resolveTagIdsFromDropdown("expense", text(acvExpenseTags), null)));
            snack(getString(R.string.capture_expense_saved));
            etExpenseAmount.setText("");
            etExpenseNote.setText("");
            acvExpenseProject.setText("");
            if (acvExpenseTags != null) {
                acvExpenseTags.setText("");
            }
        } catch (Exception e) {
            snack(getString(R.string.capture_save_failed, safeErrorMessage(e)));
        }
    }

    // ── Learning form ───────────────────────────

    private void bindLearningForm(View v) {
        etLearningDate = v.findViewById(R.id.et_learning_date);
        etLearningStart = v.findViewById(R.id.et_learning_start);
        etLearningEnd = v.findViewById(R.id.et_learning_end);
        etLearningContent = v.findViewById(R.id.et_learning_content);
        etLearningDuration = v.findViewById(R.id.et_learning_duration);
        etLearningAiRatio = v.findViewById(R.id.et_learning_ai_ratio);
        etLearningNote = v.findViewById(R.id.et_learning_note);
        etLearningEfficiencyScore = v.findViewById(R.id.et_learning_efficiency_score);
        acvLearningLevel = v.findViewById(R.id.acv_learning_level);
        acvLearningProject = v.findViewById(R.id.et_learning_project);
        acvLearningTags = v.findViewById(R.id.acv_learning_tags);

        etLearningDate.setText(LocalDate.now().format(DATE_FMT));
        etLearningDate.setOnClickListener(x -> pickDate(etLearningDate));
        etLearningStart.setOnClickListener(x -> pickDateTime(etLearningStart));
        etLearningEnd.setOnClickListener(x -> pickDateTime(etLearningEnd));

        String[] levelLabels = {
                getString(R.string.capture_learning_level_input),
                getString(R.string.capture_learning_level_applied),
                getString(R.string.capture_learning_level_result)
        };
        String[] levelVals = { "input", "applied", "result" };
        setupDropdown(acvLearningLevel, levelLabels);
        setupProjectDropdown(acvLearningProject);
        setupTagDropdown(acvLearningTags, "learning", null);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_learning);
        btnSubmit.setOnClickListener(x -> submitLearning(levelLabels, levelVals));
    }

    private void submitLearning(String[] levelLabels, String[] levelVals) {
        try {
            String occurredOn = text(etLearningDate);
            String startedAt = learningDateTimeOrNull(etLearningStart);
            String endedAt = learningDateTimeOrNull(etLearningEnd);
            if (startedAt == null || endedAt == null) {
                throw new IllegalArgumentException(getString(R.string.capture_error_learning_start_end_required));
            }
            String content = text(etLearningContent);
            int duration = resolveLearningDurationMinutes(startedAt, endedAt, text(etLearningDuration));
            String lvlLabel = text(acvLearningLevel);
            String lvlVal = mapCategoryValue(lvlLabel, levelLabels, levelVals);
            Integer efficiencyScore = parseOptionalScore(text(etLearningEfficiencyScore));
            Integer aiRatio = parseOptionalPercentage(text(etLearningAiRatio));
            if (efficiencyScore == null) {
                throw new IllegalArgumentException(getString(R.string.capture_error_learning_efficiency_required));
            }
            if (aiRatio == null) {
                throw new IllegalArgumentException(getString(R.string.capture_error_learning_ai_ratio_required));
            }
            String note = text(etLearningNote);

            graph.useCases.createLearning.execute(new CreateLearningInput(
                    occurredOn,
                    startedAt,
                    endedAt,
                    content,
                    duration,
                    efficiencyScore,
                    lvlVal,
                    aiRatio,
                    note,
                    resolveProjectAllocationsFromDropdown(text(acvLearningProject)),
                    resolveTagIdsFromDropdown("learning", text(acvLearningTags), null)));
            snack(getString(R.string.capture_learning_saved));
            etLearningDate.setText(LocalDate.now().format(DATE_FMT));
            etLearningStart.setText("");
            etLearningEnd.setText("");
            etLearningContent.setText("");
            etLearningDuration.setText("");
            acvLearningProject.setText("");
            etLearningEfficiencyScore.setText("");
            etLearningAiRatio.setText("");
            etLearningNote.setText("");
            if (acvLearningTags != null) {
                acvLearningTags.setText("");
            }
        } catch (Exception e) {
            snack(getString(R.string.capture_save_failed, safeErrorMessage(e)));
        }
    }

    // ── Project form ────────────────────────────

    private void bindProjectForm(View v) {
        etProjectName = v.findViewById(R.id.et_project_name);
        etProjectStart = v.findViewById(R.id.et_project_start);
        acvProjectTags = v.findViewById(R.id.acv_project_tags);
        etProjectStart.setText(LocalDate.now().format(DATE_FMT));
        etProjectStart.setOnClickListener(x -> pickDate(etProjectStart));
        setupTagDropdown(acvProjectTags, "project", null);

        MaterialButton btnSubmit = v.findViewById(R.id.btn_submit_project);
        btnSubmit.setOnClickListener(x -> submitProject());
    }

    private void submitProject() {
        try {
            String name = text(etProjectName);
            String start = text(etProjectStart);
            if (name.isEmpty()) {
                snack(getString(R.string.capture_project_name_required));
                return;
            }

            graph.useCases.createProject.execute(new CreateProjectInput(
                    name, start, "active", 0, null, getString(R.string.capture_note_created_from_capture),
                    resolveTagIdsFromDropdown("project", text(acvProjectTags), null)));
            snack(getString(R.string.capture_project_created, name));
            etProjectName.setText("");
            if (acvProjectTags != null) {
                acvProjectTags.setText("");
            }
            loadProjectOptions(); // refresh project chips in other forms
        } catch (Exception e) {
            snack(getString(R.string.capture_create_failed, safeErrorMessage(e)));
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
        tvAiPreviewLabel = aiView.findViewById(R.id.tv_ai_preview_label);
        llAiDraftContainer = aiView.findViewById(R.id.ll_ai_draft_container);
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
            snack(getString(R.string.capture_content_required));
            return;
        }

        btnAiParse.setEnabled(false);
        btnAiParse.setText(R.string.capture_ai_parsing);
        progressAiParse.setVisibility(View.VISIBLE);
        tvAiFeedback.setVisibility(View.GONE);
        cardAiPreview.setVisibility(View.GONE);

        ParserMode mode = getSelectedParserMode();
        graph.parserSettingsStore.saveMode(mode);
        graph.aiParseOrchestrator.setParserMode(mode);

        String contextDate = etAiDate.getText() == null ? LocalDate.now().toString()
                : etAiDate.getText().toString().trim();

        // Build context with available categories and projects
        List<String> categories = new ArrayList<>();
        categories.add("work");
        categories.add("learning");
        categories.add("life");
        categories.add("entertainment");
        categories.add("rest");
        categories.add("social");
        categories.add("salary");
        categories.add("project");
        categories.add("investment");
        categories.add("necessary");
        categories.add("experience");
        categories.add("subscription");

        List<String> projectNames = new ArrayList<>();
        if (projectOptions != null) {
            for (ProjectOption p : projectOptions)
                projectNames.add(p.name);
        }

        ParserContext context = new ParserContext(categories, projectNames, null);

        new Thread(() -> {
            ParseResult result = graph.aiParseOrchestrator.parse(raw, safeDate(contextDate), context);
            if (getActivity() == null)
                return;
            getActivity().runOnUiThread(() -> {
                latestParseResult = result;
                btnAiParse.setEnabled(true);
                btnAiParse.setText(R.string.capture_ai_parse);
                progressAiParse.setVisibility(View.GONE);

                boolean hasItems = result != null && result.items != null && !result.items.isEmpty();
                btnAiCommit.setEnabled(hasItems);
                cardAiPreview.setVisibility(View.VISIBLE);
                renderDraftItems(result);

                if (hasItems) {
                    int savedClicks = result.items.size() * 5;
                    tvAiFeedback.setText(getString(R.string.capture_ai_saved_clicks, savedClicks));
                    tvAiFeedback.setVisibility(View.VISIBLE);
                    tvAiPreviewLabel.setText(getString(R.string.capture_ai_confirm_draft, result.items.size()));
                } else {
                    tvAiFeedback.setVisibility(View.GONE);
                    tvAiPreviewLabel.setText(R.string.capture_ai_no_items_found);
                }

                snack(getString(R.string.capture_ai_parse_completed, hasItems ? result.items.size() : 0));
            });
        }).start();
    }

    private void renderDraftItems(ParseResult result) {
        if (llAiDraftContainer == null)
            return;
        llAiDraftContainer.removeAllViews();
        if (result == null || result.items == null || result.items.isEmpty()) {
            tvAiPreview.setVisibility(View.VISIBLE);
            tvAiPreview.setText(R.string.capture_ai_no_items_parsed);
            return;
        }
        tvAiPreview.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int i = 0; i < result.items.size(); i++) {
            ParseDraftItem item = result.items.get(i);
            View v = inflater.inflate(R.layout.item_ai_draft, llAiDraftContainer, false);

            ImageView ivIcon = v.findViewById(R.id.iv_item_icon);
            TextView tvTitle = v.findViewById(R.id.tv_item_title);
            TextView tvSubtitle = v.findViewById(R.id.tv_item_subtitle);
            ImageButton btnDelete = v.findViewById(R.id.btn_item_delete);

            String kind = item.kind != null ? item.kind : "unknown";
            int iconRes = R.drawable.ic_cat_project;
            String title = kind.toUpperCase(Locale.US);
            StringBuilder subtitle = new StringBuilder();

            Map<String, String> p = item.payload;
            switch (kind) {
                case "income":
                    iconRes = R.drawable.ic_cat_income;
                    title = getString(
                            R.string.capture_ai_preview_income_title,
                            valueOr(p, "amount", "0"),
                            valueOr(p, "source", "?"));
                    subtitle.append(getString(R.string.capture_ai_preview_type, valueOr(p, "type", "other")));
                    break;
                case "expense":
                    iconRes = R.drawable.ic_cat_expense;
                    title = getString(R.string.capture_ai_preview_expense_title, valueOr(p, "amount", "0"));
                    subtitle.append(getString(R.string.capture_ai_preview_category, valueOr(p, "category", "necessary")))
                            .append(" | ").append(getString(R.string.capture_ai_preview_note, valueOr(p, "note", "")));
                    break;
                case "learning":
                    iconRes = R.drawable.ic_cat_learning;
                    title = getString(R.string.capture_ai_preview_learning_title, valueOr(p, "content", "?"));
                    subtitle.append(getString(R.string.capture_ai_preview_duration, valueOr(p, "duration_minutes", "60")));
                    break;
                case "time_log":
                    iconRes = R.drawable.ic_cat_time;
                    title = getString(R.string.capture_ai_preview_time_title, valueOr(p, "description", "?"));
                    subtitle.append(valueOr(p, "start_hour", "?")).append("-").append(valueOr(p, "end_hour", "?"))
                            .append(" | ").append(getString(R.string.capture_ai_preview_category,
                                    valueOr(p, "category", "work")));
                    break;
            }

            ivIcon.setImageResource(iconRes);
            tvTitle.setText(title);
            tvSubtitle.setText(subtitle.toString());

            final int index = i;
            btnDelete.setOnClickListener(x -> {
                result.items.remove(index);
                renderDraftItems(result);
                btnAiCommit.setEnabled(!result.items.isEmpty());
                tvAiPreviewLabel.setText(getString(R.string.capture_ai_confirm_draft, result.items.size()));
            });

            llAiDraftContainer.addView(v);
        }
    }

    private void commitAiResult() {
        if (latestParseResult == null || latestParseResult.items == null) {
            snack(getString(R.string.capture_ai_no_content_to_commit));
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
            snack(getString(R.string.capture_ai_commit_success, ok));
            etAiRaw.setText("");
            cardAiPreview.setVisibility(View.GONE);
            tvAiFeedback.setVisibility(View.GONE);
            btnAiCommit.setEnabled(false);
            latestParseResult = null;
        } else {
            snack(getString(R.string.capture_ai_commit_partial, ok, fails.size()));
            StringBuilder sb = new StringBuilder(tvAiPreview.getText().toString());
            sb.append("\n\n").append(getString(R.string.capture_ai_commit_errors_header)).append('\n');
            for (String f : fails)
                sb.append("✗ ").append(f).append('\n');
            tvAiPreview.setText(sb.toString());
        }
    }

    private void commitOneAiItem(ParseDraftItem item, String contextDate) {
        Map<String, String> p = item.payload;
        switch (item.kind) {
            case "income":
                graph.useCases.createIncome.execute(new CreateIncomeInput(
                        contextDate,
                        valueOr(p, "source", getString(R.string.capture_source_ai_parse)),
                        valueOr(p, "type", "other"),
                        toCents(valueOr(p, "amount", "0")),
                        false, null, getString(R.string.capture_note_ai_capture), null, null));
                break;
            case "expense":
                graph.useCases.createExpense.execute(new CreateExpenseInput(
                        contextDate,
                        valueOr(p, "category", "necessary"),
                        toCents(valueOr(p, "amount", "0")),
                        null,
                        valueOr(p, "note", getString(R.string.capture_note_ai_capture)),
                        null,
                        null));
                break;
            case "learning":
                Integer learningAiRatio = parseOptionalPercentage(valueOr(p, "ai_ratio", ""));
                Integer learningEfficiencyScore = parseOptionalScore(valueOr(p, "efficiency_score", ""));
                if (learningAiRatio == null) {
                    learningAiRatio = 0;
                }
                if (learningEfficiencyScore == null) {
                    learningEfficiencyScore = 5;
                }
                String learningStartAt = valueOr(p, "start_hour", "").isEmpty() ? null : buildAiStartAt(p, contextDate);
                String learningEndAt = valueOr(p, "end_hour", "").isEmpty() ? null : buildAiEndAt(p, contextDate);
                if (learningStartAt == null) {
                    learningStartAt = buildAiStartAt(p, contextDate);
                }
                if (learningEndAt == null) {
                    learningEndAt = buildAiEndAt(p, contextDate);
                }
                graph.useCases.createLearning.execute(new CreateLearningInput(
                        contextDate,
                        learningStartAt,
                        learningEndAt,
                        valueOr(p, "content", getString(R.string.common_learning)),
                        FormParsers.parseInt(valueOr(p, "duration_minutes", "60"), 60),
                        learningEfficiencyScore,
                        valueOr(p, "application_level", "input"),
                        learningAiRatio,
                        valueOr(p, "note", getString(R.string.capture_note_ai_capture)), null, null));
                break;
            case "time_log":
                String startAt = buildAiStartAt(p, contextDate);
                String endAt = buildAiEndAt(p, contextDate);
                Integer timeAiRatio = parseOptionalPercentage(valueOr(p, "ai_ratio", ""));
                Integer timeEfficiencyScore = parseOptionalScore(valueOr(p, "efficiency_score", ""));
                Integer timeValueScore = parseOptionalScore(valueOr(p, "value_score", ""));
                Integer timeStateScore = parseOptionalScore(valueOr(p, "state_score", ""));
                String parsedCategory = valueOr(p, "category", "work");
                if (("work".equalsIgnoreCase(parsedCategory) || "learning".equalsIgnoreCase(parsedCategory))) {
                    if (timeAiRatio == null) {
                        timeAiRatio = 0;
                    }
                    if (timeValueScore == null) {
                        timeValueScore = 5;
                    }
                    if (timeStateScore == null) {
                        timeStateScore = 5;
                    }
                }
                graph.useCases.createTimeLog.execute(new CreateTimeLogInput(
                        startAt, endAt,
                        parsedCategory,
                        timeEfficiencyScore, timeValueScore, timeStateScore,
                        timeAiRatio,
                        valueOr(p, "description", getString(R.string.capture_source_ai_parse)),
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

    private void setupProjectDropdown(AutoCompleteTextView dropdown) {
        if (dropdown == null) {
            return;
        }
        loadProjectOptions();
        List<String> projectNames = new ArrayList<>();
        for (ProjectOption option : projectOptions) {
            if (option == null || option.name == null || option.name.trim().isEmpty()) {
                continue;
            }
            projectNames.add(option.name.trim());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, projectNames);
        dropdown.setAdapter(adapter);
        dropdown.setThreshold(1);
        dropdown.setOnClickListener(v -> dropdown.showDropDown());
    }

    private void setupTagDropdown(MultiAutoCompleteTextView dropdown, String scope, @Nullable String timeCategory) {
        if (dropdown == null) {
            return;
        }
        List<TagItem> tags = graph.useCases.getTags.execute(scope, true);
        if ("time".equals(scope) && timeCategory != null) {
            List<TagItem> visibleTags = filterTimeTagsByCategory(tags, timeCategory);
            if (!visibleTags.isEmpty()) {
                tags = visibleTags;
            }
        }
        List<String> labels = new ArrayList<>();
        if (tags != null) {
            for (TagItem tag : tags) {
                if (tag == null) {
                    continue;
                }
                labels.add(formatTagLabel(tag));
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, labels);
        dropdown.setAdapter(adapter);
        dropdown.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        dropdown.setThreshold(1);
        dropdown.setOnClickListener(v -> dropdown.showDropDown());
    }

    private List<ProjectAllocation> resolveProjectAllocationsFromDropdown(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        if (value.contains(":")) {
            return FormParsers.parseAllocations(value);
        }
        List<String> tokens = splitComma(value);
        if (tokens.isEmpty()) {
            return null;
        }
        List<ProjectAllocation> allocations = new ArrayList<>();
        for (String token : tokens) {
            String projectId = resolveProjectId(token);
            if (projectId == null || projectId.isEmpty()) {
                continue;
            }
            allocations.add(new ProjectAllocation(projectId, 1.0));
        }
        return allocations.isEmpty() ? null : allocations;
    }

    private String resolveProjectId(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String token = input.trim();
        for (ProjectOption option : projectOptions) {
            if (option == null) {
                continue;
            }
            if (token.equals(option.id)) {
                return option.id;
            }
            if (option.name != null && option.name.trim().equalsIgnoreCase(token)) {
                return option.id;
            }
        }
        return token;
    }

    private List<String> resolveTagIdsFromDropdown(String scope, String raw, @Nullable String timeCategory) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        List<TagItem> tags = graph.useCases.getTags.execute(scope, true);
        if ("time".equals(scope) && timeCategory != null) {
            List<TagItem> visibleTags = filterTimeTagsByCategory(tags, timeCategory);
            if (!visibleTags.isEmpty()) {
                tags = visibleTags;
            }
        }
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        java.util.LinkedHashMap<String, String> lookup = new java.util.LinkedHashMap<>();
        for (TagItem tag : tags) {
            if (tag == null || tag.id == null) {
                continue;
            }
            lookup.put(normalizeLookupKey(tag.id), tag.id);
            if (tag.name != null) {
                lookup.put(normalizeLookupKey(tag.name), tag.id);
            }
            lookup.put(normalizeLookupKey(formatTagLabel(tag)), tag.id);
        }
        List<String> out = new ArrayList<>();
        for (String token : splitComma(raw)) {
            String id = lookup.get(normalizeLookupKey(token));
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static String normalizeLookupKey(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim().toLowerCase(Locale.US);
        if (text.startsWith("↳")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    private static List<String> splitComma(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        String[] chunks = raw.split(",");
        for (String chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String token = chunk.trim();
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return out;
    }

    private static String formatTagLabel(TagItem tag) {
        if (tag == null) {
            return "";
        }
        String emoji = tag.emoji == null || tag.emoji.isEmpty() ? "" : tag.emoji + " ";
        String prefix = tag.level >= 2 ? "↳ " : "";
        String name = tag.name == null ? "" : tag.name;
        return prefix + emoji + name;
    }

    private static List<TagItem> filterTimeTagsByCategory(List<TagItem> tags, String timeCategory) {
        List<TagItem> visible = new ArrayList<>();
        if (tags == null || tags.isEmpty()) {
            return visible;
        }
        String normalizedCategory = timeCategory == null ? "" : timeCategory.trim().toLowerCase(Locale.US);
        if (normalizedCategory.isEmpty()) {
            visible.addAll(tags);
            return visible;
        }
        List<String> aliases = categoryAliases(normalizedCategory);
        List<String> rootIds = new ArrayList<>();
        for (TagItem tag : tags) {
            if (tag == null || tag.level > 1) {
                continue;
            }
            String name = tag.name == null ? "" : tag.name.trim().toLowerCase(Locale.US);
            String group = tag.tagGroup == null ? "" : tag.tagGroup.trim().toLowerCase(Locale.US);
            if (aliases.contains(name) || aliases.contains(group)) {
                rootIds.add(tag.id);
            }
        }
        if (rootIds.isEmpty()) {
            visible.addAll(tags);
            return visible;
        }
        for (TagItem tag : tags) {
            if (tag == null) {
                continue;
            }
            if (tag.level <= 1 && rootIds.contains(tag.id)) {
                visible.add(tag);
                continue;
            }
            if (tag.level >= 2 && tag.parentTagId != null && rootIds.contains(tag.parentTagId)) {
                visible.add(tag);
            }
        }
        return visible;
    }

    private static List<String> categoryAliases(String category) {
        List<String> aliases = new ArrayList<>();
        aliases.add(category);
        switch (category) {
            case "work":
                aliases.add("工作");
                break;
            case "learning":
                aliases.add("学习");
                break;
            case "life":
                aliases.add("生活");
                break;
            case "entertainment":
                aliases.add("娱乐");
                break;
            case "rest":
                aliases.add("休息");
                break;
            case "social":
                aliases.add("社交");
                break;
            default:
                break;
        }
        return aliases;
    }

    private void setupDropdown(AutoCompleteTextView acv, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, items);
        acv.setAdapter(adapter);
        if (items.length > 0)
            acv.setText(items[0], false);
    }

    private String mapCategoryValue(String label, String[] labels, String[] vals) {
        if (vals == null || vals.length == 0) {
            return "";
        }
        if (labels == null || labels.length != vals.length || label == null) {
            return vals[0];
        }
        String normalized = label.trim();
        for (int i = 0; i < labels.length; i++) {
            String candidate = labels[i] == null ? "" : labels[i].trim();
            if (candidate.equalsIgnoreCase(normalized)) {
                return vals[i];
            }
        }
        return vals[0];
    }

    private void validateWorkLearningRequired(String category, Integer valueScore, Integer stateScore, Integer aiRatio) {
        if (!"work".equalsIgnoreCase(category)) {
            return;
        }
        if (valueScore == null) {
            throw new IllegalArgumentException(getString(R.string.capture_error_time_value_required));
        }
        if (stateScore == null) {
            throw new IllegalArgumentException(getString(R.string.capture_error_time_state_required));
        }
        if (aiRatio == null) {
            throw new IllegalArgumentException(getString(R.string.capture_error_time_ai_ratio_required));
        }
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

    private String learningDateTimeOrNull(TextInputEditText target) {
        String value = text(target);
        if (value.isEmpty()) {
            return null;
        }
        return toUtcInstant(value);
    }

    private int resolveLearningDurationMinutes(String startedAt, String endedAt, String manualDurationText) {
        boolean hasStart = startedAt != null && !startedAt.isEmpty();
        boolean hasEnd = endedAt != null && !endedAt.isEmpty();
        if (hasStart || hasEnd) {
            if (!hasStart || !hasEnd) {
                throw new IllegalArgumentException(getString(R.string.capture_error_learning_start_end_required));
            }
            long minutes = Duration.between(java.time.Instant.parse(startedAt), java.time.Instant.parse(endedAt))
                    .toMinutes();
            if (minutes <= 0) {
                throw new IllegalArgumentException(getString(R.string.capture_error_learning_end_after_start));
            }
            return (int) minutes;
        }
        int duration = FormParsers.parseInt(manualDurationText, 0);
        if (duration <= 0) {
            throw new IllegalArgumentException(getString(R.string.capture_error_learning_duration_or_range));
        }
        return duration;
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

    private Integer parseOptionalPercentage(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        int value = FormParsers.parseInt(raw.trim(), -1);
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(getString(R.string.capture_error_ai_ratio_range));
        }
        return value;
    }

    private Integer parseOptionalScore(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        int value = FormParsers.parseInt(raw.trim(), -1);
        if (value < 1 || value > 10) {
            throw new IllegalArgumentException(getString(R.string.capture_error_score_range));
        }
        return value;
    }

    private void snack(String msg) {
        if (root != null)
            Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
    }

    private String safeErrorMessage(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            return getString(R.string.common_unknown_error);
        }
        return e.getMessage();
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
