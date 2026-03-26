package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import com.example.skyeos.domain.usecase.LifeOsUseCases;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.R;
import com.example.skyeos.domain.model.ReviewReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TimeAllocationAdapter extends RecyclerView.Adapter<TimeAllocationAdapter.ViewHolder> {

    private final List<ReviewReport.TimeCategoryAllocation> items = new ArrayList<>();

    public void submitList(List<ReviewReport.TimeCategoryAllocation> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review_allocation, parent, false);
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
        TextView tvCategoryName;
        TextView tvCategoryPercent;
        ProgressBar pbAllocation;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryName = itemView.findViewById(R.id.tv_category_name);
            tvCategoryPercent = itemView.findViewById(R.id.tv_category_percent);
            pbAllocation = itemView.findViewById(R.id.pb_allocation);
        }

        public void bind(ReviewReport.TimeCategoryAllocation item) {
            int mins = (int) item.minutes;
            String timeStr = mins >= 60 ? String.format(Locale.getDefault(), "%dh %dm", mins / 60, mins % 60)
                    : mins + "m";
            tvCategoryName.setText(item.categoryName + " (" + timeStr + ")");
            tvCategoryPercent.setText(String.format(Locale.US, "%.1f%%", item.percentage));
            pbAllocation.setProgress((int) item.percentage);

            // Assign color based on category
            int color = 0;
            switch (item.categoryName.toLowerCase()) {
                case "work":
                    color = Color.parseColor("#3B82F6"); // lifeBlueLight
                    break;
                case "learning":
                    color = Color.parseColor("#8B5CF6"); // lifePurpleLight
                    break;
                case "life":
                case "social":
                    color = Color.parseColor("#10B981");
                    break;
                case "entertainment":
                    color = Color.parseColor("#F59E0B");
                    break;
                case "rest":
                    color = Color.parseColor("#EF4444"); // lifeRed
                    break;
                default:
                    color = Color.parseColor("#94A3B8"); // textHint
                    break;
            }
            pbAllocation.setProgressTintList(ColorStateList.valueOf(color));
        }
    }
}
