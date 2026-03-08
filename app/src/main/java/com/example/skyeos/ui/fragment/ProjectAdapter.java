package com.example.skyeos.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.R;
import com.example.skyeos.domain.model.ProjectOverview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

    private final List<ProjectOverview> projects = new ArrayList<>();
    private final OnProjectClickListener listener;

    public interface OnProjectClickListener {
        void onProjectClick(String projectId);
    }

    public ProjectAdapter(OnProjectClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ProjectOverview> newProjects) {
        projects.clear();
        if (newProjects != null) {
            projects.addAll(newProjects);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new ProjectViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        holder.bind(projects.get(position));
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    static class ProjectViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvScore;
        private final TextView tvStatus;
        private final TextView tvTime;
        private final TextView tvIncome;
        private final OnProjectClickListener listener;
        private String currentProjectId;

        public ProjectViewHolder(@NonNull View itemView, OnProjectClickListener listener) {
            super(itemView);
            this.listener = listener;
            tvName = itemView.findViewById(R.id.tv_project_name);
            tvScore = itemView.findViewById(R.id.tv_project_score);
            tvStatus = itemView.findViewById(R.id.tv_project_status);
            tvTime = itemView.findViewById(R.id.tv_project_time);
            tvIncome = itemView.findViewById(R.id.tv_project_income);

            itemView.setOnClickListener(v -> {
                if (listener != null && currentProjectId != null) {
                    listener.onProjectClick(currentProjectId);
                }
            });
        }

        public void bind(ProjectOverview project) {
            currentProjectId = project.id;
            tvName.setText(project.name);
            tvScore.setText("⭐ " + project.score);
            tvStatus.setText(mapStatusLabel(project.status));

            long hours = project.totalTimeMinutes / 60;
            long mins = project.totalTimeMinutes % 60;
            tvTime.setText(String.format(Locale.US, "⏱ %dh %02dm", hours, mins));

            if (project.totalIncomeCents == 0) {
                tvIncome.setText("¥0.00");
            } else if (project.totalIncomeCents % 100 == 0) {
                tvIncome.setText(String.format(Locale.US, "💰 ¥%,d", project.totalIncomeCents / 100));
            } else {
                tvIncome.setText(String.format(Locale.US, "💰 ¥%.2f", project.totalIncomeCents / 100.0));
            }
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
}
