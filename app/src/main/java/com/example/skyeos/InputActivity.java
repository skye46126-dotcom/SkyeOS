package com.example.skyeos;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.skyeos.domain.model.ProjectOption;
import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.model.input.CreateProjectInput;
import com.example.skyeos.domain.model.input.CreateTimeLogInput;
import com.example.skyeos.ui.FormParsers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class InputActivity extends AppCompatActivity {
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    private final DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private AppGraph graph;
    private TextView statusView;

    private EditText projectNameInput;
    private EditText projectStartedOnInput;

    private EditText timeStartInput;
    private EditText timeEndInput;
    private Spinner timeCategorySpinner;
    private EditText timeNoteInput;
    private EditText timeProjectAllocInput;
    private LinearLayout projectPickContainer;

    private EditText incomeAmountInput;
    private Spinner incomeTypeSpinner;
    private EditText incomeSourceInput;
    private EditText incomeProjectAllocInput;

    private EditText expenseAmountInput;
    private Spinner expenseCategorySpinner;
    private EditText expenseNoteInput;

    private EditText learningMinutesInput;
    private Spinner learningLevelSpinner;
    private EditText learningContentInput;
    private EditText learningProjectAllocInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        graph = AppGraph.getInstance(this);
        setContentView(buildContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderProjectPickers();
    }

    private ScrollView buildContentView() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 56, 32, 40);

        TextView title = new TextView(this);
        title.setText("LifeOS Input");
        title.setTextSize(22);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setPadding(0, 12, 0, 12);
        statusView.setText("Status: ready");
        root.addView(statusView);

        root.addView(navButton("Back To Dashboard", v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }));
        root.addView(navButton("Go To AI Quick Input", v -> startActivity(new Intent(this, AIQuickInputActivity.class))));
        root.addView(navButton("Go To Recent", v -> startActivity(new Intent(this, RecentActivity.class))));

        root.addView(sectionTitle("Project"));
        projectNameInput = textInput("Project name");
        projectStartedOnInput = textInput("Started on YYYY-MM-DD");
        projectStartedOnInput.setText(LocalDate.now().format(dateFormatter));
        projectStartedOnInput.setOnClickListener(v -> pickDate(projectStartedOnInput));
        root.addView(projectNameInput);
        root.addView(projectStartedOnInput);
        root.addView(fullButton("Create Project", v -> createProject()));

        root.addView(sectionTitle("Project Picker (click to append id:1.0)"));
        projectPickContainer = new LinearLayout(this);
        projectPickContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(projectPickContainer);

        root.addView(sectionTitle("Time Log"));
        LocalDateTime now = LocalDateTime.now();
        timeStartInput = textInput("Start datetime (yyyy-MM-dd HH:mm)");
        timeEndInput = textInput("End datetime (yyyy-MM-dd HH:mm)");
        timeStartInput.setText(now.minusHours(1).format(datetimeFormatter));
        timeEndInput.setText(now.format(datetimeFormatter));
        timeStartInput.setOnClickListener(v -> pickDateTime(timeStartInput));
        timeEndInput.setOnClickListener(v -> pickDateTime(timeEndInput));
        timeCategorySpinner = spinner(new String[]{"work", "learning", "life", "entertainment", "rest", "social"});
        timeNoteInput = textInput("Note");
        timeProjectAllocInput = textInput("Project allocations");
        root.addView(timeStartInput);
        root.addView(timeEndInput);
        root.addView(timeCategorySpinner);
        root.addView(timeNoteInput);
        root.addView(timeProjectAllocInput);
        root.addView(fullButton("Add Time Log", v -> createTimeLog()));

        root.addView(sectionTitle("Income"));
        incomeAmountInput = textInput("Amount cents");
        incomeTypeSpinner = spinner(new String[]{"other", "salary", "project", "investment", "system"});
        incomeSourceInput = textInput("Source name");
        incomeSourceInput.setText("manual input");
        incomeProjectAllocInput = textInput("Project allocations");
        root.addView(incomeAmountInput);
        root.addView(incomeTypeSpinner);
        root.addView(incomeSourceInput);
        root.addView(incomeProjectAllocInput);
        root.addView(fullButton("Add Income", v -> createIncome()));

        root.addView(sectionTitle("Expense"));
        expenseAmountInput = textInput("Amount cents");
        expenseCategorySpinner = spinner(new String[]{"necessary", "experience", "subscription", "investment"});
        expenseNoteInput = textInput("Note");
        root.addView(expenseAmountInput);
        root.addView(expenseCategorySpinner);
        root.addView(expenseNoteInput);
        root.addView(fullButton("Add Expense", v -> createExpense()));

        root.addView(sectionTitle("Learning"));
        learningMinutesInput = textInput("Duration minutes");
        learningLevelSpinner = spinner(new String[]{"input", "applied", "result"});
        learningContentInput = textInput("Content");
        learningProjectAllocInput = textInput("Project allocations");
        root.addView(learningMinutesInput);
        root.addView(learningLevelSpinner);
        root.addView(learningContentInput);
        root.addView(learningProjectAllocInput);
        root.addView(fullButton("Add Learning", v -> createLearning()));

        scroll.addView(root);
        return scroll;
    }

    private void renderProjectPickers() {
        projectPickContainer.removeAllViews();
        List<ProjectOption> projects = graph.useCases.getProjectOptions.execute(false);
        if (projects == null || projects.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No active projects");
            projectPickContainer.addView(empty);
            return;
        }
        for (ProjectOption project : projects) {
            Button pick = fullButton(project.name + " (" + project.id + ")", v -> appendAllocation(project.id));
            projectPickContainer.addView(pick);
        }
    }

    private void appendAllocation(String projectId) {
        appendToAllocationField(timeProjectAllocInput, projectId);
        appendToAllocationField(incomeProjectAllocInput, projectId);
        appendToAllocationField(learningProjectAllocInput, projectId);
        status("selected project " + projectId);
    }

    private void appendToAllocationField(EditText field, String projectId) {
        String origin = field.getText() == null ? "" : field.getText().toString().trim();
        String append = projectId + ":1.0";
        if (origin.isEmpty()) {
            field.setText(append);
            return;
        }
        field.setText(origin + "," + append);
    }

    private void createProject() {
        try {
            graph.useCases.createProject.execute(new CreateProjectInput(
                    projectNameInput.getText().toString(),
                    projectStartedOnInput.getText().toString(),
                    "active",
                    0,
                    null,
                    "created from input page"
            ));
            status("created project");
            projectNameInput.setText("");
            renderProjectPickers();
        } catch (Exception e) {
            status("project error: " + e.getMessage());
        }
    }

    private void createTimeLog() {
        try {
            graph.useCases.createTimeLog.execute(new CreateTimeLogInput(
                    toIsoInstant(timeStartInput.getText().toString()),
                    toIsoInstant(timeEndInput.getText().toString()),
                    String.valueOf(timeCategorySpinner.getSelectedItem()),
                    7,
                    7,
                    timeNoteInput.getText().toString(),
                    FormParsers.parseAllocations(timeProjectAllocInput.getText().toString()),
                    null
            ));
            status("created time log");
        } catch (Exception e) {
            status("time log error: " + e.getMessage());
        }
    }

    private void createIncome() {
        try {
            long amount = FormParsers.parseLong(incomeAmountInput.getText().toString(), 0L);
            graph.useCases.createIncome.execute(new CreateIncomeInput(
                    LocalDate.now().format(dateFormatter),
                    incomeSourceInput.getText().toString(),
                    String.valueOf(incomeTypeSpinner.getSelectedItem()),
                    amount,
                    false,
                    "created from input page",
                    FormParsers.parseAllocations(incomeProjectAllocInput.getText().toString())
            ));
            status("created income");
        } catch (Exception e) {
            status("income error: " + e.getMessage());
        }
    }

    private void createExpense() {
        try {
            long amount = FormParsers.parseLong(expenseAmountInput.getText().toString(), 0L);
            graph.useCases.createExpense.execute(new CreateExpenseInput(
                    LocalDate.now().format(dateFormatter),
                    String.valueOf(expenseCategorySpinner.getSelectedItem()),
                    amount,
                    expenseNoteInput.getText().toString()
            ));
            status("created expense");
        } catch (Exception e) {
            status("expense error: " + e.getMessage());
        }
    }

    private void createLearning() {
        try {
            int duration = FormParsers.parseInt(learningMinutesInput.getText().toString(), 0);
            graph.useCases.createLearning.execute(new CreateLearningInput(
                    LocalDate.now().format(dateFormatter),
                    learningContentInput.getText().toString(),
                    duration,
                    String.valueOf(learningLevelSpinner.getSelectedItem()),
                    "created from input page",
                    FormParsers.parseAllocations(learningProjectAllocInput.getText().toString()),
                    null
            ));
            status("created learning");
        } catch (Exception e) {
            status("learning error: " + e.getMessage());
        }
    }

    private String toIsoInstant(String localDateTimeText) {
        LocalDateTime time = LocalDateTime.parse(localDateTimeText.trim(), datetimeFormatter);
        return time.atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 8;
        spinner.setLayoutParams(params);
        return spinner;
    }

    private void pickDate(EditText target) {
        LocalDate date = LocalDate.now();
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> target.setText(LocalDate.of(year, month + 1, dayOfMonth).format(dateFormatter)),
                date.getYear(),
                date.getMonthValue() - 1,
                date.getDayOfMonth()
        );
        dialog.show();
    }

    private void pickDateTime(EditText target) {
        LocalDateTime now = LocalDateTime.now();
        DatePickerDialog dateDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    TimePickerDialog timeDialog = new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> target.setText(
                                    LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute).format(datetimeFormatter)
                            ),
                            now.getHour(),
                            now.getMinute(),
                            true
                    );
                    timeDialog.show();
                },
                now.getYear(),
                now.getMonthValue() - 1,
                now.getDayOfMonth()
        );
        dateDialog.show();
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(18);
        title.setPadding(0, 18, 0, 8);
        return title;
    }

    private EditText textInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 8;
        input.setLayoutParams(params);
        return input;
    }

    private Button navButton(String text, View.OnClickListener click) {
        Button btn = new Button(this);
        btn.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 10;
        btn.setLayoutParams(params);
        btn.setOnClickListener(click);
        return btn;
    }

    private Button fullButton(String text, View.OnClickListener click) {
        return navButton(text, click);
    }

    private void status(String text) {
        statusView.setText("Status: " + text);
    }
}
