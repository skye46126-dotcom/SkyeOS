package com.example.skyeos;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.skyeos.ai.ParseDraftItem;
import com.example.skyeos.ai.ParseResult;
import com.example.skyeos.ai.ParserMode;
import com.example.skyeos.domain.model.input.CreateExpenseInput;
import com.example.skyeos.domain.model.input.CreateIncomeInput;
import com.example.skyeos.domain.model.input.CreateLearningInput;
import com.example.skyeos.domain.model.input.CreateTimeLogInput;
import com.example.skyeos.ui.FormParsers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AIQuickInputActivity extends AppCompatActivity {
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    private AppGraph graph;

    private Spinner parserModeSpinner;
    private EditText contextDateInput;
    private EditText rawTextInput;
    private TextView statusView;
    private TextView previewView;

    private ParseResult latestResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.graph = AppGraph.getInstance(this);
        setContentView(buildContentView());
        loadModeToSpinner();
    }

    private ScrollView buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 56, 32, 40);

        TextView title = new TextView(this);
        title.setText("LifeOS AI Quick Input");
        title.setTextSize(22);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setPadding(0, 12, 0, 12);
        statusView.setText("Status: ready");
        root.addView(statusView);

        root.addView(fullButton("Back To Dashboard", v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }));
        root.addView(fullButton("Go To Manual Input", v -> startActivity(new Intent(this, InputActivity.class))));

        parserModeSpinner = spinner(new String[]{"auto", "vcp", "rule"});
        root.addView(parserModeSpinner);

        contextDateInput = textInput("Context date YYYY-MM-DD");
        contextDateInput.setText(LocalDate.now().format(dateFormatter));
        root.addView(contextDateInput);

        rawTextInput = new EditText(this);
        rawTextInput.setHint("Paste natural language notes here...");
        rawTextInput.setMinLines(8);
        rawTextInput.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams rawParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rawParams.bottomMargin = 10;
        rawTextInput.setLayoutParams(rawParams);
        root.addView(rawTextInput);

        root.addView(fullButton("Parse (AI/Rule)", v -> parseNow()));
        root.addView(fullButton("Commit Parsed Entries", v -> commitParsed()));

        previewView = new TextView(this);
        previewView.setPadding(0, 14, 0, 20);
        previewView.setText("-");
        root.addView(previewView);

        scrollView.addView(root);
        return scrollView;
    }

    private void loadModeToSpinner() {
        ParserMode mode = graph.parserSettingsStore.loadMode();
        parserModeSpinner.setSelection(mode == ParserMode.VCP ? 1 : mode == ParserMode.RULE ? 2 : 0);
    }

    private void parseNow() {
        String raw = value(rawTextInput);
        if (raw.isEmpty()) {
            status("parse failed: empty input");
            return;
        }
        ParserMode mode = selectedMode();
        graph.parserSettingsStore.saveMode(mode);
        graph.aiParseOrchestrator.setParserMode(mode);
        latestResult = graph.aiParseOrchestrator.parse(raw, safeDate(value(contextDateInput)));
        previewView.setText(formatPreview(latestResult));
        status("parsed with " + latestResult.parserUsed + ", items=" + latestResult.items.size());
    }

    private void commitParsed() {
        if (latestResult == null || latestResult.items == null || latestResult.items.isEmpty()) {
            status("commit failed: nothing parsed");
            return;
        }
        int ok = 0;
        List<String> fails = new ArrayList<>();
        for (ParseDraftItem item : latestResult.items) {
            if (item == null || item.kind == null || "unknown".equals(item.kind)) {
                continue;
            }
            try {
                commitOne(item, safeDate(value(contextDateInput)));
                ok++;
            } catch (Exception e) {
                fails.add(item.kind + ": " + e.getMessage());
            }
        }
        if (fails.isEmpty()) {
            status("commit success: " + ok);
        } else {
            status("commit partial: success=" + ok + ", fail=" + fails.size());
            StringBuilder sb = new StringBuilder(formatPreview(latestResult));
            sb.append("\n\nCommit errors:\n");
            for (String f : fails) {
                sb.append("- ").append(f).append('\n');
            }
            previewView.setText(sb.toString());
        }
    }

    private void commitOne(ParseDraftItem item, String contextDate) {
        Map<String, String> p = item.payload;
        switch (item.kind) {
            case "income":
                graph.useCases.createIncome.execute(new CreateIncomeInput(
                        contextDate,
                        valueOr(p, "source", "AI parsed"),
                        valueOr(p, "type", "other"),
                        toCents(valueOr(p, "amount", "0")),
                        false,
                        "from ai quick input",
                        null
                ));
                return;
            case "expense":
                graph.useCases.createExpense.execute(new CreateExpenseInput(
                        contextDate,
                        valueOr(p, "category", "necessary"),
                        toCents(valueOr(p, "amount", "0")),
                        valueOr(p, "note", "from ai quick input")
                ));
                return;
            case "learning":
                graph.useCases.createLearning.execute(new CreateLearningInput(
                        contextDate,
                        valueOr(p, "content", "learning"),
                        FormParsers.parseInt(valueOr(p, "duration_minutes", "60"), 60),
                        valueOr(p, "application_level", "input"),
                        "from ai quick input",
                        null,
                        null
                ));
                return;
            case "time_log":
                String startAt = buildStartAt(p, contextDate);
                String endAt = buildEndAt(p, contextDate, startAt);
                graph.useCases.createTimeLog.execute(new CreateTimeLogInput(
                        startAt,
                        endAt,
                        valueOr(p, "category", "work"),
                        7,
                        7,
                        valueOr(p, "description", "from ai quick input"),
                        null,
                        null
                ));
                return;
            default:
        }
    }

    private String buildStartAt(Map<String, String> payload, String contextDate) {
        String hour = payload == null ? "" : payload.get("start_hour");
        if (hour != null && !hour.trim().isEmpty()) {
            int h = FormParsers.parseInt(hour, 9);
            LocalDateTime dt = LocalDateTime.parse(contextDate + String.format(Locale.US, " %02d:00", h), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return dt.atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        int duration = FormParsers.parseInt(payload == null ? "1" : payload.get("duration_hours"), 1);
        LocalDateTime start = now.minusHours(Math.max(1, duration));
        return start.atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
    }

    private String buildEndAt(Map<String, String> payload, String contextDate, String startAt) {
        String hour = payload == null ? "" : payload.get("end_hour");
        if (hour != null && !hour.trim().isEmpty()) {
            int h = FormParsers.parseInt(hour, 10);
            LocalDateTime dt = LocalDateTime.parse(contextDate + String.format(Locale.US, " %02d:00", h), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return dt.atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
        }
        int duration = FormParsers.parseInt(payload == null ? "1" : payload.get("duration_hours"), 1);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now;
        if (duration <= 0) {
            duration = 1;
        }
        return end.atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString();
    }

    private static String formatPreview(ParseResult result) {
        if (result == null || result.items == null || result.items.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("requestId: ").append(result.requestId).append('\n');
        sb.append("parserUsed: ").append(result.parserUsed).append('\n');
        if (result.warnings != null && !result.warnings.isEmpty()) {
            sb.append("warnings:\n");
            for (String w : result.warnings) {
                sb.append("- ").append(w).append('\n');
            }
        }
        sb.append('\n');
        for (ParseDraftItem item : result.items) {
            sb.append("[")
                    .append(item.kind)
                    .append("] conf=")
                    .append(String.format(Locale.US, "%.2f", item.confidence))
                    .append(" source=")
                    .append(item.source)
                    .append('\n');
            for (Map.Entry<String, String> e : item.payload.entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            if (item.warning != null && !item.warning.trim().isEmpty()) {
                sb.append("  warning: ").append(item.warning).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
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
        params.bottomMargin = 10;
        spinner.setLayoutParams(params);
        return spinner;
    }

    private EditText textInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 10;
        input.setLayoutParams(params);
        return input;
    }

    private Button fullButton(String text, android.view.View.OnClickListener click) {
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

    private ParserMode selectedMode() {
        Object selected = parserModeSpinner.getSelectedItem();
        return ParserMode.fromString(selected == null ? "auto" : selected.toString());
    }

    private static String safeDate(String raw) {
        try {
            return LocalDate.parse(raw.trim()).toString();
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    private static String value(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static String valueOr(Map<String, String> map, String key, String fallback) {
        if (map == null) {
            return fallback;
        }
        String value = map.get(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static long toCents(String rawAmount) {
        String normalized = rawAmount == null ? "0" : rawAmount.trim();
        if (normalized.isEmpty()) {
            return 0L;
        }
        if (normalized.contains(".")) {
            double v = Double.parseDouble(normalized);
            return (long) Math.round(v * 100.0);
        }
        return Long.parseLong(normalized);
    }

    private void status(String text) {
        statusView.setText("Status: " + text);
    }
}

