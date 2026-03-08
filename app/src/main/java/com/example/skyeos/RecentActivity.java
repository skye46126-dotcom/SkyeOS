package com.example.skyeos;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.skyeos.domain.model.RecentRecordItem;

import java.util.List;

public class RecentActivity extends AppCompatActivity {
    private AppGraph graph;
    private TextView recordsView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        graph = AppGraph.getInstance(this);
        setContentView(buildContentView());
        refresh();
    }

    private ScrollView buildContentView() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 56, 32, 40);

        TextView title = new TextView(this);
        title.setText("LifeOS Recent");
        title.setTextSize(22);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setPadding(0, 12, 0, 12);
        root.addView(statusView);

        root.addView(fullButton("Back To Dashboard", v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }));
        root.addView(fullButton("Go To Input", v -> startActivity(new Intent(this, InputActivity.class))));
        root.addView(fullButton("Refresh Recent", v -> refresh()));

        recordsView = new TextView(this);
        recordsView.setPadding(0, 14, 0, 24);
        root.addView(recordsView);

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

    private void refresh() {
        List<RecentRecordItem> list = graph.useCases.getRecentRecords.execute(80);
        if (list == null || list.isEmpty()) {
            statusView.setText("Status: empty");
            recordsView.setText("-");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (RecentRecordItem item : list) {
            sb.append(item.occurredAt)
                    .append(" | ")
                    .append(item.type)
                    .append(" | ")
                    .append(item.title)
                    .append(" | ")
                    .append(item.detail == null ? "" : item.detail)
                    .append('\n');
        }
        statusView.setText("Status: loaded " + list.size() + " records");
        recordsView.setText(sb.toString());
    }
}

