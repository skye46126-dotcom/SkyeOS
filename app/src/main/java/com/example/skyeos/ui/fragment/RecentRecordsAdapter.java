package com.example.skyeos.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.R;
import com.example.skyeos.domain.model.RecentRecordItem;

import java.util.ArrayList;
import java.util.List;

public class RecentRecordsAdapter extends RecyclerView.Adapter<RecentRecordsAdapter.RecordViewHolder> {

    private final List<RecentRecordItem> records = new ArrayList<>();

    public void submitList(List<RecentRecordItem> newRecords) {
        records.clear();
        if (newRecords != null) {
            records.addAll(newRecords);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // We'll reuse item_project.xml but strip some info, or create a simple text
        // view item.
        // A minimal text-based row for the timeline.
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        holder.bind(records.get(position));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvSubtitle;

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(android.R.id.text1);
            tvSubtitle = itemView.findViewById(android.R.id.text2);
        }

        public void bind(RecentRecordItem record) {
            if ("meta".equals(record.type)) {
                tvTitle.setText(record.title);
                tvSubtitle.setText(record.detail == null ? "" : record.detail);
                return;
            }
            switch (record.type) {
                case "time_log":
                    tvTitle.setText("⏱ " + record.title);
                    break;
                case "time":
                    tvTitle.setText("⏱ " + record.title);
                    break;
                case "income":
                    tvTitle.setText("💰 " + record.title);
                    break;
                case "expense":
                    tvTitle.setText("💸 " + record.title);
                    break;
                case "learning":
                    tvTitle.setText("📚 " + record.title);
                    break;
                default:
                    tvTitle.setText(record.type + " - " + record.title);
                    break;
            }
            tvSubtitle.setText(record.occurredAt + " " + (record.detail != null ? record.detail : ""));
        }
    }
}
