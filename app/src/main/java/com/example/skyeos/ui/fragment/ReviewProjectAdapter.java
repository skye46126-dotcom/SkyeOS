package com.example.skyeos.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.R;
import com.example.skyeos.domain.model.ProjectProgressItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReviewProjectAdapter extends RecyclerView.Adapter<ReviewProjectAdapter.ViewHolder> {

    private final List<ProjectProgressItem> items = new ArrayList<>();

    public void submitList(List<ProjectProgressItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review_project, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProjectName;
        TextView tvProjectHourly;
        TextView tvProjectTime;
        TextView tvProjectIncome;
        TextView tvProjectRoi;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProjectName = itemView.findViewById(R.id.tv_project_name);
            tvProjectHourly = itemView.findViewById(R.id.tv_project_hourly);
            tvProjectTime = itemView.findViewById(R.id.tv_project_time);
            tvProjectIncome = itemView.findViewById(R.id.tv_project_income);
            tvProjectRoi = itemView.findViewById(R.id.tv_project_roi);
        }

        public void bind(ProjectProgressItem item) {
            tvProjectName.setText(item.projectName);

            if (item.hourlyRateYuan > 0) {
                tvProjectHourly.setText(String.format(Locale.US, "¥%.1f/h", item.hourlyRateYuan));
                tvProjectHourly.setVisibility(View.VISIBLE);
            } else {
                tvProjectHourly.setVisibility(View.GONE);
            }

            int mins = (int) item.timeSpentMinutes;
            String timeStr = mins >= 60 ? String.format(Locale.getDefault(), "%dh %dm", mins / 60, mins % 60)
                    : mins + "m";
            tvProjectTime.setText("⏱ " + timeStr);

            if (item.incomeEarnedCents > 0) {
                tvProjectIncome.setText(String.format(Locale.getDefault(), "💰 ¥%.2f", item.incomeEarnedCents / 100.0));
                tvProjectIncome.setVisibility(View.VISIBLE);
            } else {
                tvProjectIncome.setVisibility(View.GONE);
            }
            tvProjectRoi.setText(String.format(
                    Locale.US,
                    "Op %.1f%% | Full %.1f%% | Direct %s | Shared %s",
                    item.operatingRoiPerc,
                    item.fullyLoadedRoiPerc,
                    formatYuan(item.directExpenseCents),
                    formatYuan(item.allocatedStructuralCostCents)));

            if ("warning".equals(item.evaluationStatus)) {
                tvProjectName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.statusNegative));
            } else if ("positive".equals(item.evaluationStatus)) {
                tvProjectName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.statusPositive));
            } else {
                tvProjectName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.textPrimary));
            }
        }

        private static String formatYuan(long cents) {
            if (cents <= 0L) {
                return "--";
            }
            if (cents % 100 == 0) {
                return String.format(Locale.US, "¥%,d", cents / 100);
            }
            return String.format(Locale.US, "¥%.2f", cents / 100.0);
        }
    }
}
