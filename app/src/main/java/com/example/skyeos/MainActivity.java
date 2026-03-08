package com.example.skyeos;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.skyeos.domain.model.BackupResult;
import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.WindowOverview;

import java.time.LocalDate;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private AppGraph graph;
    private TextView statusView;
    private TextView dashboardView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        graph = AppGraph.getInstance(this);
        setContentView(buildContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard("ready");
    }

    private ScrollView buildContentView() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 56, 32, 40);

        TextView title = new TextView(this);
        title.setText("LifeOS Dashboard");
        title.setTextSize(22);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setPadding(0, 12, 0, 12);
        root.addView(statusView);

        root.addView(fullButton("Go To Input", v -> startActivity(new Intent(this, InputActivity.class))));
        root.addView(fullButton("Go To AI Quick Input", v -> startActivity(new Intent(this, AIQuickInputActivity.class))));
        root.addView(fullButton("Go To Recent", v -> startActivity(new Intent(this, RecentActivity.class))));
        root.addView(fullButton("Go To Cloud Sync", v -> startActivity(new Intent(this, CloudSyncActivity.class))));
        root.addView(fullButton("Refresh Dashboard", v -> refreshDashboard("refreshed")));
        root.addView(fullButton("Create Manual Backup", v -> {
            BackupResult backup = graph.useCases.createBackup.execute("manual");
            refreshDashboard(backup.success ? "backup success" : "backup failed");
        }));

        dashboardView = new TextView(this);
        dashboardView.setPadding(0, 16, 0, 16);
        root.addView(dashboardView);

        scroll.addView(root);
        return scroll;
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

    private void refreshDashboard(String status) {
        String today = LocalDate.now().toString();
        MetricSnapshotSummary daySnapshot = graph.useCases.recomputeMetricSnapshot.execute(today, "day");
        MetricSnapshotSummary weekSnapshot = graph.useCases.recomputeMetricSnapshot.execute(today, "week");
        WindowOverview day = graph.useCases.getOverview.execute(today, "day");
        WindowOverview week = graph.useCases.getOverview.execute(today, "week");
        statusView.setText("Status: " + status);

        dashboardView.setText(
                "Today\n" +
                        "income(cents): " + day.totalIncomeCents + "\n" +
                        "expense(cents): " + day.totalExpenseCents + "\n" +
                        "net(cents): " + day.netIncomeCents + "\n" +
                        "work(min): " + day.totalWorkMinutes + "\n" +
                        "public time ratio: " + pct(day.publicTimeRatio) + "\n" +
                        "public income ratio: " + pct(day.publicIncomeRatio) + "\n" +
                        "public learning ratio: " + pct(day.publicLearningRatio) + "\n" +
                        "hourly(cents): " + nullableLong(daySnapshot.hourlyRateCents) + "\n" +
                        "freedom(cents): " + nullableLong(daySnapshot.freedomCents) + "\n\n" +
                        "Last 7 Days\n" +
                        "income(cents): " + week.totalIncomeCents + "\n" +
                        "expense(cents): " + week.totalExpenseCents + "\n" +
                        "net(cents): " + week.netIncomeCents + "\n" +
                        "work(min): " + week.totalWorkMinutes + "\n" +
                        "hourly(cents): " + nullableLong(weekSnapshot.hourlyRateCents) + "\n" +
                        "freedom(cents): " + nullableLong(weekSnapshot.freedomCents)
        );
    }

    private static String pct(double ratio) {
        return String.format(Locale.US, "%.1f%%", ratio * 100.0);
    }

    private static String nullableLong(Long value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
