package com.example.skyeos.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.ProjectDetail;
import com.example.skyeos.domain.model.input.CreateProjectInput;
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
        btnDelete.setOnClickListener(v -> confirmDeleteProject());
        view.findViewById(R.id.btn_edit_project).setOnClickListener(v -> showEditProjectDialog());
    }

    private void loadProjectDetail() {
        if (TextUtils.isEmpty(projectId))
            return;

        ProjectDetail detail = graph.useCases.projectUseCases.getProjectDetail(projectId);
        if (detail == null) {
            Toast.makeText(requireContext(), R.string.project_not_found, Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        tvTitle.setText(detail.name);
        tvScore.setText(getString(R.string.project_score_format, detail.score));
        tvStatus.setText(mapStatusLabel(detail.status));
        etNote.setText(detail.note);

        long hours = detail.totalTimeMinutes / 60;
        long mins = detail.totalTimeMinutes % 60;
        tvTime.setText(getString(R.string.common_duration_hours_minutes, hours, mins));

        tvRoi.setText(getString(R.string.project_roi_format, detail.operatingRoiPerc, detail.fullyLoadedRoiPerc));
        tvHourly.setText(getString(R.string.common_hourly_format, formatYuan(Math.round(detail.hourlyRateYuan * 100.0))));
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
        tvCostMethod.setText(getString(
                R.string.project_cost_method_summary,
                detail.analysisStartDate,
                detail.analysisEndDate,
                formatYuan(detail.benchmarkHourlyRateCents),
                formatYuan(detail.idealHourlyRateCents)));

        if ("done".equals(detail.status)) {
            btnMarkDone.setText(R.string.project_mark_active);
            btnMarkDone.setOnClickListener(v -> markProjectStatus("active"));
        } else {
            btnMarkDone.setText(R.string.project_mark_done);
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
        Toast.makeText(requireContext(), R.string.project_note_saved, Toast.LENGTH_SHORT).show();
    }

    private void markProjectStatus(String status) {
        String endedOn = "done".equals(status) ? java.time.LocalDate.now().toString() : "";
        graph.useCases.projectUseCases.updateProject(projectId, status, -1, null, endedOn);
        loadProjectDetail();
    }

    private void confirmDeleteProject() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.project_delete_title)
                .setMessage(R.string.project_deleted_hidden_note)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    graph.useCases.deleteProject.execute(projectId);
                    Toast.makeText(requireContext(), R.string.project_deleted, Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager().popBackStack();
                })
                .show();
    }

    private void showEditProjectDialog() {
        ProjectDetail detail = graph.useCases.projectUseCases.getProjectDetail(projectId);
        if (detail == null) {
            return;
        }
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        EditText etName = new EditText(requireContext());
        etName.setHint(R.string.project_name_hint);
        etName.setText(detail.name);
        layout.addView(etName);
        EditText etStart = new EditText(requireContext());
        etStart.setHint(R.string.project_start_date_hint);
        etStart.setText(detail.startedOn);
        layout.addView(etStart);
        EditText etStatus = new EditText(requireContext());
        etStatus.setHint(R.string.common_status);
        etStatus.setText(detail.status);
        layout.addView(etStatus);
        EditText etScore = new EditText(requireContext());
        etScore.setHint(R.string.project_score_hint);
        etScore.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etScore.setText(detail.score > 0 ? String.valueOf(detail.score) : "");
        layout.addView(etScore);
        EditText etNote2 = new EditText(requireContext());
        etNote2.setHint(R.string.common_note);
        etNote2.setText(detail.note);
        layout.addView(etNote2);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.project_edit_title)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        Integer score = etScore.getText() == null || etScore.getText().toString().trim().isEmpty()
                                ? null
                                : Integer.parseInt(etScore.getText().toString().trim());
                        graph.useCases.updateProjectRecord.execute(projectId, new CreateProjectInput(
                                etName.getText().toString().trim(),
                                etStart.getText().toString().trim(),
                                etStatus.getText().toString().trim(),
                                0,
                                score,
                                etNote2.getText().toString().trim(),
                                null));
                        loadProjectDetail();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private static String formatYuan(long cents) {
        if (cents == 0L) {
            return getString(R.string.common_none);
        }
        if (cents % 100 == 0) {
            return getString(R.string.common_currency_yuan_int, cents / 100);
        }
        return getString(R.string.common_currency_yuan, cents / 100.0);
    }

    private String buildProfitBlock(long profitCents, long costCents, long breakEvenCents, double roiPerc) {
        String label = profitCents >= 0 ? getString(R.string.project_profit_label) : getString(R.string.project_loss_label);
        return getString(
                R.string.project_profit_block,
                label,
                formatYuan(Math.abs(profitCents)),
                formatYuan(costCents),
                formatYuan(breakEvenCents),
                roiPerc);
    }

    private String mapStatusLabel(String status) {
        if ("done".equalsIgnoreCase(status)) {
            return getString(R.string.project_status_done);
        }
        if ("paused".equalsIgnoreCase(status)) {
            return getString(R.string.project_status_paused);
        }
        return getString(R.string.project_status_active);
    }
}
