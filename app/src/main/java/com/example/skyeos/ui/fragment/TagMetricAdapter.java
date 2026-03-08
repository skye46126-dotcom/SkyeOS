package com.example.skyeos.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.domain.model.ReviewReport;

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
                .inflate(android.R.layout.simple_list_item_2, parent, false);
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
        private final TextView t1;
        private final TextView t2;

        VH(@NonNull View itemView) {
            super(itemView);
            t1 = itemView.findViewById(android.R.id.text1);
            t2 = itemView.findViewById(android.R.id.text2);
        }

        void bind(ReviewReport.TagMetric item, boolean expenseMode) {
            String emoji = item.emoji == null || item.emoji.isEmpty() ? "" : item.emoji + " ";
            t1.setText(emoji + item.tagName);
            if (expenseMode) {
                t2.setText(String.format(Locale.US, "¥%.2f | %.1f%%", item.value / 100.0, item.percentage));
            } else {
                t2.setText(String.format(Locale.US, "%d 分钟 | %.1f%%", item.value, item.percentage));
            }
        }
    }
}
