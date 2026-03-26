package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import com.example.skyeos.domain.usecase.LifeOsUseCases;


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
import org.json.JSONArray;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AndroidEntryPoint
public class CaptureFragment extends Fragment {

    @Inject
    CurrentUserContext userContext;

    @Inject
    LifeOsDatabase database;

    @Inject
    LifeOsUseCases useCases;

    @Inject
    com.example.skyeos.ai.AiParseOrchestrator aiParseOrchestrator;

    @Inject
    com.example.skyeos.ai.ParserSettingsStore parserSettingsStore;
    private static final String ARG_INITIAL_TYPE = "initial_type";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern NUMBER_TOKEN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern CLOCK_TOKEN = Pattern.compile(
            "(?i)(上午|早上|中午|下午|晚上|凌晨|傍晚|am|pm)?\\s*(\\d{1,2})(?:[:点时](\\d{1,2}))?(半)?\\s*(am|pm)?");
    private static final Pattern FULL_DATE_TOKEN = Pattern.compile("(\\d{4})\\s*[/-]\\s*(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DAY_TOKEN = Pattern.compile("(\\d{1,2})\\s*[月/-]\\s*(\\d{1,2})\\s*日?");

    
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

            useCases.createTimeLog.execute(new CreateTimeLogInput(
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
            useCases.createTimeLog.execute(new CreateTimeLogInput(
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

            useCases.createIncome.execute(new CreateIncomeInput(
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

            useCases.createExpense.execute(new CreateExpenseInput(
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

            useCases.createLearning.execute(new CreateLearningInput(
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

            useCases.createProject.execute(new CreateProjectInput(
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
        ParserMode saved = parserSettingsStore.loadMode();
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
        parserSettingsStore.saveMode(mode);
        aiParseOrchestrator.setParserMode(mode);

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
            for (ProjectOption p : projectOptions) {
                if (p == null || p.name == null || p.name.trim().isEmpty()) {
                    continue;
                }
                projectNames.add(p.name.trim());
            }
        }

        List<String> tagNames = new ArrayList<>();
        for (String scope : new String[] { "global", "time", "income", "expense", "learning", "project" }) {
            List<TagItem> tags = useCases.getTags.execute(scope, true);
            if (tags == null) {
                continue;
            }
            for (TagItem tag : tags) {
                if (tag == null || tag.name == null || tag.name.trim().isEmpty()) {
                    continue;
                }
                String name = tag.name.trim();
                if (!tagNames.contains(name)) {
                    tagNames.add(name);
                }
            }
        }

        ParserContext context = new ParserContext(categories, projectNames, tagNames);

        new Thread(() -> {
            ParseResult result = aiParseOrchestrator.parse(raw, safeDate(contextDate), context);
            if (getActivity() == null)
                return;
            getActivity().runOnUiThread(() -> {
                latestParseResult = result;
                btnAiParse.setEnabled(true);
                btnAiParse.setText(R.string.capture_ai_parse);
                progressAiParse.setVisibility(View.GONE);

                boolean hasItems = result != null && result.items != null && !result.items.isEmpty();
                btnAiCommit.setEnabled(hasCommittableItems(result));
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
            ImageButton btnEdit = v.findViewById(R.id.btn_item_edit);
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
                    String learningStart = valueOr(p, "start_time", valueOr(p, "start_hour", ""));
                    String learningEnd = valueOr(p, "end_time", valueOr(p, "end_hour", ""));
                    if (!learningStart.isEmpty() || !learningEnd.isEmpty()) {
                        subtitle.append(" | ").append(learningStart.isEmpty() ? "?" : learningStart)
                                .append("-").append(learningEnd.isEmpty() ? "?" : learningEnd);
                    }
                    break;
                case "time_log":
                    iconRes = R.drawable.ic_cat_time;
                    title = getString(R.string.capture_ai_preview_time_title, valueOr(p, "description", "?"));
                    String timeStart = valueOr(p, "start_time", valueOr(p, "start_hour", "?"));
                    String timeEnd = valueOr(p, "end_time", valueOr(p, "end_hour", "?"));
                    subtitle.append(timeStart).append("-").append(timeEnd)
                            .append(" | ").append(getString(R.string.capture_ai_preview_category,
                                    valueOr(p, "category", "work")));
                    break;
                default:
                    title = getString(R.string.capture_ai_preview_unknown_title);
                    subtitle.append(valueOr(p, "raw", ""));
                    break;
            }

            ivIcon.setImageResource(iconRes);
            tvTitle.setText(title);
            tvSubtitle.setText(subtitle.toString());

            final int index = i;
            btnEdit.setOnClickListener(x -> showDraftEditDialog(result, index));
            btnDelete.setOnClickListener(x -> {
                result.items.remove(index);
                renderDraftItems(result);
                btnAiCommit.setEnabled(hasCommittableItems(result));
                tvAiPreviewLabel.setText(getString(R.string.capture_ai_confirm_draft, result.items.size()));
            });

            llAiDraftContainer.addView(v);
        }
    }

    private void showDraftEditDialog(ParseResult result, int index) {
        if (result == null || result.items == null || index < 0 || index >= result.items.size()) {
            return;
        }
        ParseDraftItem item = result.items.get(index);
        if (item == null) {
            return;
        }
        Map<String, String> payload = item.payload == null ? new HashMap<>() : new HashMap<>(item.payload);
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        String[] kindLabels = {
                "time_log",
                "income",
                "expense",
                "learning",
                "unknown"
        };
        String[] kindValues = {
                "time_log",
                "income",
                "expense",
                "learning",
                "unknown"
        };
        AutoCompleteTextView acvKind = dialogDropdownField(getString(R.string.common_type), layout, kindLabels);
        String normalizedKind = normalizeDraftKind(item.kind);
        acvKind.setText(mapValueToLabel(normalizedKind, kindLabels, kindValues), false);

        EditText etDate = dialogEditField(getString(R.string.common_date), valueOr(payload, "date", ""),
                InputType.TYPE_CLASS_TEXT, layout);

        EditText etTimeCategory = dialogEditField(getString(R.string.common_category), valueOr(payload, "category", ""),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etTimeStartTime = dialogEditField(getString(R.string.capture_ai_field_start_hour),
                valueOr(payload, "start_time", valueOr(payload, "start_hour", "")),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etTimeEndTime = dialogEditField(getString(R.string.capture_ai_field_end_hour),
                valueOr(payload, "end_time", valueOr(payload, "end_hour", "")),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etTimeDurationMinutes = dialogEditField(getString(R.string.capture_ai_field_duration_minutes),
                valueOr(payload, "duration_minutes", valueOr(payload, "duration_hours", "")),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etTimeDescription = dialogEditField(getString(R.string.capture_ai_field_description),
                valueOr(payload, "description", ""),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etTimeAi = dialogEditField(getString(R.string.capture_time_ai_ratio), valueOr(payload, "ai_ratio", ""),
                InputType.TYPE_CLASS_NUMBER, layout);
        EditText etTimeEfficiency = dialogEditField(getString(R.string.capture_time_efficiency_score),
                valueOr(payload, "efficiency_score", ""),
                InputType.TYPE_CLASS_NUMBER, layout);
        EditText etTimeValue = dialogEditField(getString(R.string.capture_time_value_score), valueOr(payload, "value_score", ""),
                InputType.TYPE_CLASS_NUMBER, layout);
        EditText etTimeState = dialogEditField(getString(R.string.capture_time_state_score), valueOr(payload, "state_score", ""),
                InputType.TYPE_CLASS_NUMBER, layout);

        EditText etIncomeSource = dialogEditField(getString(R.string.common_source), valueOr(payload, "source", ""),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etIncomeType = dialogEditField(getString(R.string.common_type), valueOr(payload, "type", ""),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etIncomeAmount = dialogEditField(getString(R.string.common_amount_cny), valueOr(payload, "amount", ""),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etIncomeAi = dialogEditField(getString(R.string.capture_time_ai_ratio), valueOr(payload, "ai_ratio", ""),
                InputType.TYPE_CLASS_NUMBER, layout);
        EditText etIncomeNote = dialogEditField(getString(R.string.common_note), valueOr(payload, "note", ""),
                InputType.TYPE_CLASS_TEXT, layout);

        EditText etExpenseCategory = dialogEditField(getString(R.string.common_category), valueOr(payload, "category", ""),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etExpenseAmount = dialogEditField(getString(R.string.common_amount_cny), valueOr(payload, "amount", ""),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, layout);
        EditText etExpenseAi = dialogEditField(getString(R.string.capture_time_ai_ratio), valueOr(payload, "ai_ratio", ""),
                InputType.TYPE_CLASS_NUMBER, layout);
        EditText etExpenseNote = dialogEditField(getString(R.string.common_note), valueOr(payload, "note", ""),
                InputType.TYPE_CLASS_TEXT, layout);

        EditText etLearningContent = dialogEditField(getString(R.string.common_content), valueOr(payload, "content", ""),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etLearningDuration = dialogEditField(getString(R.string.capture_ai_field_duration_minutes),
                valueOr(payload, "duration_minutes", ""),
                InputType.TYPE_CLASS_NUMBER, layout);
        EditText etLearningLevel = dialogEditField(getString(R.string.capture_ai_field_application_level),
                valueOr(payload, "application_level", ""),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etLearningStartTime = dialogEditField(getString(R.string.capture_ai_field_start_hour),
                valueOr(payload, "start_time", valueOr(payload, "start_hour", "")),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etLearningEndTime = dialogEditField(getString(R.string.capture_ai_field_end_hour),
                valueOr(payload, "end_time", valueOr(payload, "end_hour", "")),
                InputType.TYPE_CLASS_TEXT, layout);
        EditText etLearningEfficiency = dialogEditField(getString(R.string.capture_learning_efficiency_score),
                valueOr(payload, "efficiency_score", ""),
                InputType.TYPE_CLASS_NUMBER, layout);
        EditText etLearningAi = dialogEditField(getString(R.string.capture_learning_ai_ratio), valueOr(payload, "ai_ratio", ""),
                InputType.TYPE_CLASS_NUMBER, layout);
        EditText etLearningNote = dialogEditField(getString(R.string.common_note), valueOr(payload, "note", ""),
                InputType.TYPE_CLASS_TEXT, layout);

        EditText etUnknownRaw = dialogEditField(getString(R.string.capture_ai_field_raw), valueOr(payload, "raw", ""),
                InputType.TYPE_CLASS_TEXT, layout);

        View[] timeViews = {
                etTimeCategory, etTimeStartTime, etTimeEndTime, etTimeDurationMinutes, etTimeDescription,
                etTimeAi, etTimeEfficiency, etTimeValue, etTimeState
        };
        View[] incomeViews = {
                etIncomeSource, etIncomeType, etIncomeAmount, etIncomeAi, etIncomeNote
        };
        View[] expenseViews = {
                etExpenseCategory, etExpenseAmount, etExpenseAi, etExpenseNote
        };
        View[] learningViews = {
                etLearningContent, etLearningDuration, etLearningLevel, etLearningStartTime, etLearningEndTime,
                etLearningEfficiency, etLearningAi, etLearningNote
        };
        View[] unknownViews = { etUnknownRaw };

        Runnable syncKindVisibility = () -> {
            String selectedKind = normalizeDraftKind(mapCategoryValue(text(acvKind), kindLabels, kindValues));
            setViewsVisibility(timeViews, "time_log".equals(selectedKind));
            setViewsVisibility(incomeViews, "income".equals(selectedKind));
            setViewsVisibility(expenseViews, "expense".equals(selectedKind));
            setViewsVisibility(learningViews, "learning".equals(selectedKind));
            setViewsVisibility(unknownViews, "unknown".equals(selectedKind));
        };
        acvKind.setOnClickListener(v -> acvKind.showDropDown());
        acvKind.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                syncKindVisibility.run();
            }
        });
        syncKindVisibility.run();

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.capture_ai_edit_draft)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    String selectedKind = normalizeDraftKind(mapCategoryValue(text(acvKind), kindLabels, kindValues));
                    Map<String, String> updatedPayload = new HashMap<>();
                    putIfNotBlank(updatedPayload, "date", etDate.getText().toString().trim());
                    switch (selectedKind) {
                        case "time_log":
                            putIfNotBlank(updatedPayload, "category", etTimeCategory.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "start_time", etTimeStartTime.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "end_time", etTimeEndTime.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "duration_minutes", etTimeDurationMinutes.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "description", etTimeDescription.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "ai_ratio", etTimeAi.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "efficiency_score", etTimeEfficiency.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "value_score", etTimeValue.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "state_score", etTimeState.getText().toString().trim());
                            break;
                        case "income":
                            putIfNotBlank(updatedPayload, "source", etIncomeSource.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "type", etIncomeType.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "amount", etIncomeAmount.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "ai_ratio", etIncomeAi.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "note", etIncomeNote.getText().toString().trim());
                            break;
                        case "expense":
                            putIfNotBlank(updatedPayload, "category", etExpenseCategory.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "amount", etExpenseAmount.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "ai_ratio", etExpenseAi.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "note", etExpenseNote.getText().toString().trim());
                            break;
                        case "learning":
                            putIfNotBlank(updatedPayload, "content", etLearningContent.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "duration_minutes", etLearningDuration.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "application_level", etLearningLevel.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "start_time", etLearningStartTime.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "end_time", etLearningEndTime.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "efficiency_score", etLearningEfficiency.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "ai_ratio", etLearningAi.getText().toString().trim());
                            putIfNotBlank(updatedPayload, "note", etLearningNote.getText().toString().trim());
                            break;
                        default:
                            putIfNotBlank(updatedPayload, "raw", etUnknownRaw.getText().toString().trim());
                            selectedKind = "unknown";
                            break;
                    }
                    result.items.set(index, new ParseDraftItem(
                            selectedKind,
                            updatedPayload,
                            item.confidence,
                            item.source,
                            item.warning));
                    renderDraftItems(result);
                    btnAiCommit.setEnabled(hasCommittableItems(result));
                })
                .show();
    }

    private EditText dialogEditField(String hint, String value, int inputType, LinearLayout parent) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setInputType(inputType);
        input.setText(value == null ? "" : value);
        parent.addView(input);
        return input;
    }

    private AutoCompleteTextView dialogDropdownField(String hint, LinearLayout parent, String[] options) {
        com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(
                requireContext(),
                null,
                com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox_ExposedDropdownMenu);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 32, 0, 0);
        til.setLayoutParams(lp);
        til.setHint(hint);
        com.google.android.material.textfield.MaterialAutoCompleteTextView acv =
                new com.google.android.material.textfield.MaterialAutoCompleteTextView(til.getContext());
        acv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        acv.setInputType(InputType.TYPE_NULL);
        acv.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, options));
        til.addView(acv);
        parent.addView(til);
        return acv;
    }

    private static void setViewsVisibility(View[] views, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        for (View view : views) {
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    private static void putIfNotBlank(Map<String, String> payload, String key, String value) {
        if (payload == null || key == null) {
            return;
        }
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        payload.put(key, value.trim());
    }

    private static String mapValueToLabel(String value, String[] labels, String[] values) {
        if (value == null || labels == null || values == null || labels.length != values.length || labels.length == 0) {
            return labels != null && labels.length > 0 ? labels[0] : "";
        }
        String normalized = value.trim().toLowerCase(Locale.US);
        for (int i = 0; i < values.length; i++) {
            if (normalized.equals(values[i])) {
                return labels[i];
            }
        }
        return labels[0];
    }

    private static String normalizeDraftKind(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "unknown";
        }
        String normalized = raw.trim().toLowerCase(Locale.US);
        if ("time".equals(normalized)) {
            return "time_log";
        }
        if ("time_log".equals(normalized) || "income".equals(normalized) || "expense".equals(normalized)
                || "learning".equals(normalized) || "unknown".equals(normalized)) {
            return normalized;
        }
        return "unknown";
    }

    private static boolean hasCommittableItems(ParseResult result) {
        if (result == null || result.items == null || result.items.isEmpty()) {
            return false;
        }
        for (ParseDraftItem item : result.items) {
            if (item == null) {
                continue;
            }
            if (!"unknown".equals(normalizeDraftKind(item.kind))) {
                return true;
            }
        }
        return false;
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
            if (item == null || "unknown".equals(normalizeDraftKind(item.kind)))
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
        Map<String, String> p = item.payload == null ? new HashMap<>() : item.payload;
        String itemDate = resolveItemDate(p, contextDate);
        String itemKind = normalizeDraftKind(item.kind);
        switch (itemKind) {
            case "income":
                String normalizedIncomeType = normalizeIncomeType(valueOr(p, "type", "other"));
                useCases.createIncome.execute(new CreateIncomeInput(
                        itemDate,
                        valueOr(p, "source", valueOr(p, "source_name", getString(R.string.capture_source_ai_parse))),
                        normalizedIncomeType,
                        parseAiAmountCents(p),
                        parseBooleanText(valueOr(p, "is_passive", "")),
                        parseOptionalPercentage(valueOr(p, "ai_ratio", "")),
                        valueOr(p, "note", getString(R.string.capture_note_ai_capture)),
                        resolveAiProjectAllocations(p),
                        resolveAiTagIds("income", p, null)));
                break;
            case "expense":
                String normalizedExpenseCategory = normalizeExpenseCategory(valueOr(p, "category", "necessary"));
                long expenseAmountCents = parseAiAmountCents(p);
                if (expenseAmountCents <= 0) {
                    throw new IllegalArgumentException(getString(R.string.capture_error_expense_amount_gt_zero));
                }
                useCases.createExpense.execute(new CreateExpenseInput(
                        itemDate,
                        normalizedExpenseCategory,
                        expenseAmountCents,
                        parseOptionalPercentage(valueOr(p, "ai_ratio", "")),
                        valueOr(p, "note", getString(R.string.capture_note_ai_capture)),
                        resolveAiProjectAllocations(p),
                        resolveAiTagIds("expense", p, null)));
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
                int learningFallbackMinutes = resolveAiDurationMinutes(p, 60);
                AiTimeWindow learningWindow = resolveAiTimeWindow(p, itemDate, learningFallbackMinutes);
                useCases.createLearning.execute(new CreateLearningInput(
                        itemDate,
                        learningWindow.startedAtUtc,
                        learningWindow.endedAtUtc,
                        valueOr(p, "content", valueOr(p, "description", getString(R.string.common_learning))),
                        learningWindow.durationMinutes,
                        learningEfficiencyScore,
                        normalizeLearningLevel(valueOr(p, "application_level", "input")),
                        learningAiRatio,
                        valueOr(p, "note", getString(R.string.capture_note_ai_capture)),
                        resolveAiProjectAllocations(p),
                        resolveAiTagIds("learning", p, null)));
                break;
            case "time_log":
                int timeFallbackMinutes = resolveAiDurationMinutes(p, 60);
                AiTimeWindow timeWindow = resolveAiTimeWindow(p, itemDate, timeFallbackMinutes);
                Integer timeAiRatio = parseOptionalPercentage(valueOr(p, "ai_ratio", ""));
                Integer timeEfficiencyScore = parseOptionalScore(valueOr(p, "efficiency_score", ""));
                Integer timeValueScore = parseOptionalScore(valueOr(p, "value_score", ""));
                Integer timeStateScore = parseOptionalScore(valueOr(p, "state_score", ""));
                String parsedCategory = normalizeTimeCategory(valueOr(p, "category", "work"));
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
                useCases.createTimeLog.execute(new CreateTimeLogInput(
                        timeWindow.startedAtUtc, timeWindow.endedAtUtc,
                        parsedCategory,
                        timeEfficiencyScore, timeValueScore, timeStateScore,
                        timeAiRatio,
                        valueOr(p, "description", valueOr(p, "note", getString(R.string.capture_source_ai_parse))),
                        resolveAiProjectAllocations(p),
                        resolveAiTagIds("time", p, parsedCategory)));
                break;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // HELPERS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void loadProjectOptions() {
        projectOptions = useCases.getProjectOptions.execute(false);
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
        List<TagItem> tags = useCases.getTags.execute(scope, true);
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
        List<TagItem> tags = useCases.getTags.execute(scope, true);
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
        String prefix = tag.level >= 2 ? "↳ " : "";
        String name = tag.name == null ? "" : tag.name;
        return prefix + name;
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

    private String resolveItemDate(Map<String, String> payload, String fallbackDate) {
        String dateRaw = firstNonBlank(payload, "date", "occurred_on", "occurredOn");
        return normalizeDateWithContext(dateRaw, safeDate(fallbackDate));
    }

    private String normalizeDateWithContext(String raw, String fallbackDate) {
        String fallback = safeDate(fallbackDate);
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        String text = raw.trim();
        LocalDate anchor = LocalDate.parse(fallback);
        if (text.contains("前天")) {
            return anchor.minusDays(2).toString();
        }
        if (text.contains("昨天")) {
            return anchor.minusDays(1).toString();
        }
        if (text.contains("明天")) {
            return anchor.plusDays(1).toString();
        }
        if (text.contains("后天")) {
            return anchor.plusDays(2).toString();
        }
        if (text.contains("今天")) {
            return anchor.toString();
        }
        try {
            return LocalDate.parse(text).toString();
        } catch (Exception ignored) {
        }
        Matcher fullDate = FULL_DATE_TOKEN.matcher(text);
        if (fullDate.find()) {
            try {
                int y = Integer.parseInt(fullDate.group(1));
                int m = Integer.parseInt(fullDate.group(2));
                int d = Integer.parseInt(fullDate.group(3));
                return LocalDate.of(y, m, d).toString();
            } catch (Exception ignored) {
            }
        }
        Matcher monthDay = MONTH_DAY_TOKEN.matcher(text);
        if (monthDay.find()) {
            try {
                int m = Integer.parseInt(monthDay.group(1));
                int d = Integer.parseInt(monthDay.group(2));
                return LocalDate.of(anchor.getYear(), m, d).toString();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private List<ProjectAllocation> resolveAiProjectAllocations(Map<String, String> payload) {
        String raw = firstNonBlank(payload, "project_allocations", "project_names", "projects", "project");
        String normalized = normalizeMultiValue(raw);
        if (normalized.isEmpty()) {
            return null;
        }
        return resolveProjectAllocationsFromDropdown(normalized);
    }

    private List<String> resolveAiTagIds(String scope, Map<String, String> payload, @Nullable String timeCategory) {
        String raw = firstNonBlank(payload, "tag_ids", "tag_names", "tags", "tag");
        String normalized = normalizeMultiValue(raw);
        if (normalized.isEmpty()) {
            return null;
        }
        return resolveTagIdsFromDropdown(scope, normalized, timeCategory);
    }

    private String normalizeMultiValue(String raw) {
        List<String> tokens = parseListTokens(raw);
        if (tokens.isEmpty()) {
            return "";
        }
        return String.join(",", tokens);
    }

    private List<String> parseListTokens(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        String text = raw.trim();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                JSONArray arr = new JSONArray(text);
                for (int i = 0; i < arr.length(); i++) {
                    String token = arr.optString(i, "").trim();
                    if (!token.isEmpty()) {
                        unique.add(token);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (unique.isEmpty()) {
            String[] chunks = text.split("[,，、;；\\n|]");
            for (String chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                String token = chunk.trim();
                if (!token.isEmpty()) {
                    unique.add(token);
                }
            }
        }
        out.addAll(unique);
        return out;
    }

    private String normalizeTimeCategory(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (text.contains("learn") || text.contains("学习") || text.contains("阅读") || text.contains("课程")) {
            return "learning";
        }
        if (text.contains("life") || text.contains("生活") || text.contains("家务") || text.contains("通勤")) {
            return "life";
        }
        if (text.contains("entertain") || text.contains("娱乐") || text.contains("电影") || text.contains("游戏")) {
            return "entertainment";
        }
        if (text.contains("rest") || text.contains("休息") || text.contains("睡")) {
            return "rest";
        }
        if (text.contains("social") || text.contains("社交") || text.contains("朋友") || text.contains("聚会")) {
            return "social";
        }
        return "work";
    }

    private String normalizeIncomeType(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (text.contains("salary") || text.contains("工资") || text.contains("薪")) {
            return "salary";
        }
        if (text.contains("project") || text.contains("项目") || text.contains("回款") || text.contains("外包")) {
            return "project";
        }
        if (text.contains("invest") || text.contains("投资") || text.contains("分红")) {
            return "investment";
        }
        if (text.contains("system") || text.contains("系统") || text.contains("补贴")) {
            return "system";
        }
        return "other";
    }

    private String normalizeExpenseCategory(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (text.contains("subscription") || text.contains("订阅") || text.contains("会员")) {
            return "subscription";
        }
        if (text.contains("invest") || text.contains("投资")) {
            return "investment";
        }
        if (text.contains("experience") || text.contains("体验") || text.contains("娱乐")
                || text.contains("旅游") || text.contains("聚餐")) {
            return "experience";
        }
        return "necessary";
    }

    private String normalizeLearningLevel(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (text.contains("result") || text.contains("成果") || text.contains("产出")) {
            return "result";
        }
        if (text.contains("apply") || text.contains("applied") || text.contains("实践")
                || text.contains("应用") || text.contains("落地")) {
            return "applied";
        }
        return "input";
    }

    private long parseAiAmountCents(Map<String, String> payload) {
        String centsRaw = firstNonBlank(payload, "amount_cents");
        Double explicitCents = parseFirstDecimal(centsRaw);
        if (explicitCents != null && explicitCents >= 0) {
            return Math.max(0L, Math.round(explicitCents));
        }
        String amountRaw = firstNonBlank(payload, "amount", "amount_yuan", "money");
        if (amountRaw.isEmpty()) {
            return 0L;
        }
        Double number = parseFirstDecimal(amountRaw);
        if (number == null || number < 0) {
            return 0L;
        }
        String lower = amountRaw.toLowerCase(Locale.US);
        if (lower.contains("分") && !lower.contains("元") && !lower.contains("块")) {
            return Math.max(0L, Math.round(number));
        }
        double yuan = number;
        if (lower.contains("万") || lower.matches(".*\\bw\\b.*")) {
            yuan *= 10_000.0;
        } else if (lower.contains("千") || lower.matches(".*\\bk\\b.*")) {
            yuan *= 1_000.0;
        }
        return Math.max(0L, Math.round(yuan * 100.0));
    }

    private int resolveAiDurationMinutes(Map<String, String> payload, int fallbackMinutes) {
        Integer minutes = parseDurationTextMinutes(firstNonBlank(payload,
                "duration_minutes", "duration", "minutes", "duration_min"));
        if (minutes != null && minutes > 0) {
            return minutes;
        }
        Double hours = parseFirstDecimal(firstNonBlank(payload, "duration_hours", "hours"));
        if (hours != null && hours > 0) {
            return Math.max(1, (int) Math.round(hours * 60.0));
        }
        return Math.max(1, fallbackMinutes);
    }

    private Integer parseDurationTextMinutes(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Double number = parseFirstDecimal(raw);
        if (number == null || number <= 0) {
            return null;
        }
        String text = raw.toLowerCase(Locale.US);
        if (text.contains("小时") || text.contains("hour") || text.matches(".*\\bh\\b.*")) {
            return Math.max(1, (int) Math.round(number * 60.0));
        }
        if (text.contains("分钟") || text.contains("min") || text.matches(".*\\bm\\b.*")) {
            return Math.max(1, (int) Math.round(number));
        }
        if (text.contains(".") && number <= 12) {
            return Math.max(1, (int) Math.round(number * 60.0));
        }
        return Math.max(1, (int) Math.round(number));
    }

    private AiTimeWindow resolveAiTimeWindow(Map<String, String> payload, String itemDate, int fallbackDurationMinutes) {
        LocalDate baseDate = LocalDate.parse(safeDate(itemDate));
        ZonedDateTime start = parseAiDateTime(firstNonBlank(payload,
                "started_at", "start_at", "start_time", "start", "start_hour"), baseDate);
        ZonedDateTime end = parseAiDateTime(firstNonBlank(payload,
                "ended_at", "end_at", "end_time", "end", "end_hour"), baseDate);
        int durationMinutes = Math.max(1, fallbackDurationMinutes);
        if (start == null && end == null) {
            start = baseDate.atTime(9, 0).atZone(APP_ZONE);
            end = start.plusMinutes(durationMinutes);
        } else if (start != null && end == null) {
            end = start.plusMinutes(durationMinutes);
        } else if (start == null) {
            start = end.minusMinutes(durationMinutes);
        }
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
            if (!end.isAfter(start)) {
                end = start.plusMinutes(Math.max(15, durationMinutes));
            }
        }
        int resolvedMinutes = Math.max(1, (int) Duration.between(start, end).toMinutes());
        return new AiTimeWindow(
                start.withZoneSameInstant(ZoneOffset.UTC).toInstant().toString(),
                end.withZoneSameInstant(ZoneOffset.UTC).toInstant().toString(),
                resolvedMinutes);
    }

    private ZonedDateTime parseAiDateTime(String raw, LocalDate fallbackDate) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        try {
            return Instant.parse(text).atZone(APP_ZONE);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(APP_ZONE);
        } catch (Exception ignored) {
        }
        DateTimeFormatter[] localFormatters = new DateTimeFormatter[] {
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DT_FMT,
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        };
        for (DateTimeFormatter formatter : localFormatters) {
            try {
                return LocalDateTime.parse(text, formatter).atZone(APP_ZONE);
            } catch (Exception ignored) {
            }
        }
        LocalTime localTime = parseAiLocalTime(text);
        if (localTime != null) {
            return fallbackDate.atTime(localTime).atZone(APP_ZONE);
        }
        return null;
    }

    private LocalTime parseAiLocalTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        try {
            return LocalTime.parse(text, DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception ignored) {
        }
        try {
            return LocalTime.parse(text, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ignored) {
        }
        if (text.matches("\\d{1,2}")) {
            int hour = FormParsers.parseInt(text, -1);
            if (hour >= 0 && hour <= 23) {
                return LocalTime.of(hour, 0);
            }
        }
        Matcher matcher = CLOCK_TOKEN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String period = safeText(matcher.group(1));
        int hour;
        try {
            hour = Integer.parseInt(safeText(matcher.group(2)));
        } catch (Exception ignored) {
            return null;
        }
        int minute = 0;
        String minuteRaw = safeText(matcher.group(3));
        if (!minuteRaw.isEmpty()) {
            minute = FormParsers.parseInt(minuteRaw, 0);
        } else if (!safeText(matcher.group(4)).isEmpty()) {
            minute = 30;
        }
        String suffix = safeText(matcher.group(5));
        if (!suffix.isEmpty()) {
            period = suffix;
        }
        hour = normalizeHourByPeriod(hour, period);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private int normalizeHourByPeriod(int hour, String periodRaw) {
        String period = periodRaw == null ? "" : periodRaw.trim().toLowerCase(Locale.US);
        if (period.isEmpty()) {
            return hour;
        }
        if (period.contains("pm") || period.contains("下午") || period.contains("晚上") || period.contains("傍晚")) {
            if (hour < 12) {
                return hour + 12;
            }
            return hour;
        }
        if (period.contains("am") || period.contains("早上") || period.contains("上午") || period.contains("凌晨")) {
            if (hour == 12) {
                return 0;
            }
            return hour;
        }
        if (period.contains("中午")) {
            if (hour < 11) {
                return hour + 12;
            }
            return hour;
        }
        return hour;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
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

    private static String firstNonBlank(Map<String, String> payload, String... keys) {
        if (payload == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String value = payload.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String valueOr(Map<String, String> m, String k, String def) {
        if (m == null)
            return def;
        String v = m.get(k);
        return v == null || v.trim().isEmpty() ? def : v.trim();
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
        Double number = parseFirstDecimal(raw);
        if (number == null) {
            return null;
        }
        if (number > 0 && number <= 1.0 && raw.contains(".")) {
            number = number * 100.0;
        }
        int value = (int) Math.round(number);
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(getString(R.string.capture_error_ai_ratio_range));
        }
        return value;
    }

    private Integer parseOptionalScore(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Double number = parseFirstDecimal(raw);
        if (number == null) {
            return null;
        }
        int value = (int) Math.round(number);
        if (value < 1 || value > 10) {
            throw new IllegalArgumentException(getString(R.string.capture_error_score_range));
        }
        return value;
    }

    private static Double parseFirstDecimal(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = NUMBER_TOKEN.matcher(raw.replace(",", "").replace("，", ""));
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean parseBooleanText(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        String text = raw.trim().toLowerCase(Locale.US);
        return "true".equals(text) || "1".equals(text) || text.contains("是") || text.contains("被动");
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

    private static final class AiTimeWindow {
        final String startedAtUtc;
        final String endedAtUtc;
        final int durationMinutes;

        AiTimeWindow(String startedAtUtc, String endedAtUtc, int durationMinutes) {
            this.startedAtUtc = startedAtUtc;
            this.endedAtUtc = endedAtUtc;
            this.durationMinutes = durationMinutes;
        }
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
