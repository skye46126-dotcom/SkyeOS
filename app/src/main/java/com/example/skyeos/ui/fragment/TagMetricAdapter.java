package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import com.example.skyeos.domain.usecase.LifeOsUseCases;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.domain.model.ReviewReport;
import com.example.skyeos.ui.util.CategoryIconHelper;
import com.example.skyeos.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TagMetricAdapter extends RecyclerView.Adapter<TagMetricAdapter.VH> {

    public interface OnTagClickListener {
        void onClick(ReviewReport.TagMetric metric);
    }

    private final List<ReviewReport.TagMetric> items = new ArrayList<>();
    private final boolean expenseMode;
    private final OnTagClickListener onClick;

    public TagMetricAdapter(boolean expenseMode, OnTagClickListener onClick) {
        this.expenseMode = expenseMode;
        this.onClick = onClick;
    }

    public void submitList(List<ReviewReport.TagMetric> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag_metric, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ReviewReport.TagMetric item = items.get(position);
        holder.bind(item, expenseMode);
        holder.itemView.setOnClickListener(v -> {
            if (onClick != null) {
                onClick.onClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        private final TextView tvName, tvValue, tvPercentage;
        private final android.widget.ImageView ivIcon;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_tag_name);
            tvValue = itemView.findViewById(R.id.tv_tag_value);
            tvPercentage = itemView.findViewById(R.id.tv_tag_percentage);
            ivIcon = itemView.findViewById(R.id.iv_tag_icon);
        }

        void bind(ReviewReport.TagMetric item, boolean expenseMode) {
            ivIcon.setImageResource(CategoryIconHelper.getIconRes(item.emoji));
            tvName.setText(item.tagName);
            if (expenseMode) {
                tvValue.setText(String.format(Locale.US, "¥%.2f", item.value / 100.0));
            } else {
                tvValue.setText(String.format(Locale.US, "%d min", item.value));
            }
            tvPercentage.setText(String.format(Locale.US, "%.1f%%", item.percentage));
        }
    }
}
