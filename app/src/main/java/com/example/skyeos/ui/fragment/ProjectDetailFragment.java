package com.example.skyeos.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.ProjectDetail;
import com.example.skyeos.domain.usecase.ProjectUseCases;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

public class ProjectDetailFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private String projectId;
    private AppGraph graph;

    private TextView tvTitle;
    private TextView tvScore;
    private TextView tvStatus;
    private TextView tvTime;
    private TextView tvRoi;
    private TextView tvHourly;
    private TextView tvTimeCost;
    private TextView tvDirectExpense;
    private TextView tvStructuralAllocated;
    private TextView tvOperating;
    private TextView tvFullyLoaded;
    private TextView tvCostMethod;
    private TextInputEditText etNote;
    private RecyclerView rvRecords;
    private MaterialButton btnMarkDone;
    private MaterialButton btnDelete;

    public static ProjectDetailFragment newInstance(String projectId) {
        ProjectDetailFragment fragment = new ProjectDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_ID, projectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_project_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        graph = AppGraph.getInstance(requireContext());

        if (getArguments() != null) {
            projectId = getArguments().getString(ARG_PROJECT_ID);
        }

        initViews(view);
        loadProjectDetail();
    }

    private void initViews(View view) {
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        tvTitle = view.findViewById(R.id.tv_detail_title);
        tvScore = view.findViewById(R.id.tv_detail_score);
        tvStatus = view.findViewById(R.id.tv_detail_status);
        tvTime = view.findViewById(R.id.tv_detail_time);
        tvRoi = view.findViewById(R.id.tv_detail_roi);
        tvHourly = view.findViewById(R.id.tv_detail_hourly);
        tvTimeCost = view.findViewById(R.id.tv_detail_time_cost);
        tvDirectExpense = view.findViewById(R.id.tv_detail_direct_expense);
        tvStructuralAllocated = view.findViewById(R.id.tv_detail_structural_allocated);
        tvOperating = view.findViewById(R.id.tv_detail_operating);
        tvFullyLoaded = view.findViewById(R.id.tv_detail_fully_loaded);
        tvCostMethod = view.findViewById(R.id.tv_detail_cost_method);
        etNote = view.findViewById(R.id.et_project_note);

        view.findViewById(R.id.btn_save_note).setOnClickListener(v -> saveNote());

        rvRecords = view.findViewById(R.id.rv_detail_records);
        rvRecords.setLayoutManager(new LinearLayoutManager(requireContext()));

        btnMarkDone = view.findViewById(R.id.btn_mark_done);
        btnMarkDone.setOnClickListener(v -> markProjectStatus("done"));

        btnDelete = view.findViewById(R.id.btn_delete_project);
        btnDelete.setOnClickListener(v -> {
            // TODO: call generic soft delete usecase
            Toast.makeText(requireContext(), "Delete is not available yet", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadProjectDetail() {
        if (TextUtils.isEmpty(projectId))
            return;

        ProjectDetail detail = graph.useCases.projectUseCases.getProjectDetail(projectId);
        if (detail == null) {
            Toast.makeText(requireContext(), "Project not found", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        tvTitle.setText(detail.name);
        tvScore.setText("⭐ " + detail.score);
        tvStatus.setText(mapStatusLabel(detail.status));
        etNote.setText(detail.note);

        long hours = detail.totalTimeMinutes / 60;
        long mins = detail.totalTimeMinutes % 60;
        tvTime.setText(String.format(Locale.US, "⏱ %dh %02dm", hours, mins));

        tvRoi.setText(String.format(Locale.US, "Op %.1f%% | Full %.1f%%", detail.operatingRoiPerc, detail.fullyLoadedRoiPerc));
        tvHourly.setText(String.format(Locale.US, "¥%.2f / hour", detail.hourlyRateYuan));
        tvTimeCost.setText(formatYuan(detail.timeCostCents));
        tvDirectExpense.setText(formatYuan(detail.directExpenseCents));
        tvStructuralAllocated.setText(formatYuan(detail.allocatedStructuralCostCents));
        tvOperating.setText(buildProfitBlock(
                detail.operatingProfitCents,
                detail.operatingCostCents,
                detail.operatingBreakEvenIncomeCents,
                detail.operatingRoiPerc));
        tvFullyLoaded.setText(buildProfitBlock(
                detail.fullyLoadedProfitCents,
                detail.fullyLoadedCostCents,
                detail.fullyLoadedBreakEvenIncomeCents,
                detail.fullyLoadedRoiPerc));
        tvCostMethod.setText(String.format(Locale.US,
                "Window %s → %s | Benchmark hourly %s/h | Ideal %s/h | Structural allocation by project work minutes share",
                detail.analysisStartDate,
                detail.analysisEndDate,
                formatYuan(detail.benchmarkHourlyRateCents),
                formatYuan(detail.idealHourlyRateCents)));

        if ("done".equals(detail.status)) {
            btnMarkDone.setText("Mark as Active");
            btnMarkDone.setOnClickListener(v -> markProjectStatus("active"));
        } else {
            btnMarkDone.setText("Mark as Done");
            btnMarkDone.setOnClickListener(v -> markProjectStatus("done"));
        }

        // We can reuse a generic RecentRecordAdapter if we have one,
        // or create a simple one here. For now we use the existing structure if
        // possible.
        // Assuming we'll build a RecentRecordsAdapter shortly.
        RecentRecordsAdapter adapter = new RecentRecordsAdapter();
        adapter.submitList(detail.recentRecords);
        rvRecords.setAdapter(adapter);
    }

    private void saveNote() {
        String note = etNote.getText().toString();
        graph.useCases.projectUseCases.updateProject(projectId, null, -1, note, null);
        Toast.makeText(requireContext(), "Note saved", Toast.LENGTH_SHORT).show();
    }

    private void markProjectStatus(String status) {
        String endedOn = "done".equals(status) ? java.time.LocalDate.now().toString() : "";
        graph.useCases.projectUseCases.updateProject(projectId, status, -1, null, endedOn);
        loadProjectDetail();
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

    private static String buildProfitBlock(long profitCents, long costCents, long breakEvenCents, double roiPerc) {
        String label = profitCents >= 0 ? "Profit" : "Loss";
        return String.format(
                Locale.US,
                "%s %s | Cost %s | Break-even %s | ROI %.1f%%",
                label,
                formatYuan(Math.abs(profitCents)),
                formatYuan(costCents),
                formatYuan(breakEvenCents),
                roiPerc);
    }

    private String mapStatusLabel(String status) {
        if ("done".equalsIgnoreCase(status)) {
            return "Done";
        }
        if ("paused".equalsIgnoreCase(status)) {
            return "Paused";
        }
        return "Active";
    }
}
