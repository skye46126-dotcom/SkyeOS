package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import com.example.skyeos.domain.usecase.LifeOsUseCases;


import com.example.skyeos.MainActivity;
import com.example.skyeos.R;
import com.example.skyeos.data.config.TimeGoalStore;
import com.example.skyeos.domain.model.WindowOverview;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;

@AndroidEntryPoint
public class TimeManagementFragment extends Fragment {

    @Inject
    CurrentUserContext userContext;

    @Inject
    LifeOsDatabase database;

    @Inject
    LifeOsUseCases useCases;

    @Inject
    com.example.skyeos.data.config.TimeGoalStore goalStore;
    
    
    private LocalDate selectedDate = LocalDate.now();
    private TextView tvDate;
    private TextInputEditText etGoalWorkMinutes;
    private TextInputEditText etGoalLearningMinutes;
    private TextView tvGoalStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_time_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        tvDate = view.findViewById(R.id.tv_time_mgmt_date);
        etGoalWorkMinutes = view.findViewById(R.id.et_time_goal_work_minutes);
        etGoalLearningMinutes = view.findViewById(R.id.et_time_goal_learning_minutes);
        tvGoalStatus = view.findViewById(R.id.tv_time_goal_status);

        bindGoalInputs();
        renderDate();
        renderGoalStatus();

        view.findViewById(R.id.btn_time_mgmt_pick_date).setOnClickListener(v -> pickDate());
        view.findViewById(R.id.btn_time_mgmt_open_day).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDayDetail(selectedDate.toString());
            }
        });
        view.findViewById(R.id.btn_time_mgmt_open_review).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateTo(R.id.nav_review);
            }
        });
        view.findViewById(R.id.btn_time_goal_save).setOnClickListener(v -> saveGoal(view));
    }

    private void pickDate() {
        DatePickerDialog dlg = new DatePickerDialog(
                requireContext(),
                (picker, year, month, dayOfMonth) -> {
                    selectedDate = LocalDate.of(year, month + 1, dayOfMonth);
                    renderDate();
                    renderGoalStatus();
                },
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth());
        dlg.show();
    }

    private void renderDate() {
        if (tvDate != null) {
            tvDate.setText(getString(R.string.time_mgmt_selected_date, selectedDate.toString()));
        }
    }

    private void bindGoalInputs() {
        TimeGoalStore.Goal goal = goalStore.load();
        if (etGoalWorkMinutes != null) {
            etGoalWorkMinutes.setText(String.valueOf(goal.minWorkMinutes));
        }
        if (etGoalLearningMinutes != null) {
            etGoalLearningMinutes.setText(String.valueOf(goal.minLearningMinutes));
        }
    }

    private void saveGoal(View anchor) {
        int minWork = parseNonNegativeInt(etGoalWorkMinutes);
        int minLearning = parseNonNegativeInt(etGoalLearningMinutes);
        goalStore.save(minWork, minLearning);
        renderGoalStatus();
        Snackbar.make(anchor, R.string.time_mgmt_goal_saved, Snackbar.LENGTH_SHORT).show();
    }

    private void renderGoalStatus() {
        if (tvGoalStatus == null) {
            return;
        }
        TimeGoalStore.Goal goal = goalStore.load();
        if (!goal.isConfigured()) {
            tvGoalStatus.setText(R.string.time_mgmt_goal_status_unset);
            tvGoalStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
            return;
        }
        WindowOverview overview = useCases.getOverview.execute(selectedDate.toString(), "day");
        int workMinutes = toIntMinutes(overview == null ? 0L : overview.totalWorkMinutes);
        int learningMinutes = toIntMinutes(overview == null ? 0L : overview.totalLearningMinutes);
        boolean reached = goal.isReached(workMinutes, learningMinutes);
        int colorRes = reached ? R.color.statusPositive : R.color.statusNegative;
        tvGoalStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (reached) {
            tvGoalStatus.setText(getString(
                    R.string.time_mgmt_goal_status_reached,
                    workMinutes, goal.minWorkMinutes,
                    learningMinutes, goal.minLearningMinutes));
        } else {
            tvGoalStatus.setText(getString(
                    R.string.time_mgmt_goal_status_unreached,
                    workMinutes, goal.minWorkMinutes,
                    learningMinutes, goal.minLearningMinutes));
        }
    }

    private static int parseNonNegativeInt(TextInputEditText input) {
        try {
            if (input == null || input.getText() == null) {
                return 0;
            }
            String raw = input.getText().toString().trim();
            if (raw.isEmpty()) {
                return 0;
            }
            return Math.max(0, Integer.parseInt(raw));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int toIntMinutes(long minutes) {
        if (minutes <= 0L) {
            return 0;
        }
        if (minutes > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) minutes;
    }
}
