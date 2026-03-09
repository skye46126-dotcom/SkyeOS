package com.example.skyeos.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.R;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.ui.util.CategoryIconHelper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecentRecordsAdapter extends RecyclerView.Adapter<RecentRecordsAdapter.RecordViewHolder> {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd", Locale.US);

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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_record, parent, false);
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
        private final TextView tvNote;
        private final ImageView ivIcon;

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_record_title);
            tvSubtitle = itemView.findViewById(R.id.tv_record_subtitle);
            tvNote = itemView.findViewById(R.id.tv_record_note);
            ivIcon = itemView.findViewById(R.id.iv_record_icon);
        }

        public void bind(RecentRecordItem record) {
            ivIcon.setImageResource(CategoryIconHelper.getIconRes(record.type));
            if ("meta".equals(record.type)) {
                tvTitle.setText(record.title);
                tvSubtitle.setText(record.detail == null ? "" : record.detail);
                tvNote.setVisibility(View.GONE);
                return;
            }
            tvTitle.setText(record.title);
            String subtitle = formatOccurredAt(record.occurredAt);
            ParsedDetail parsed = parseDetail(record.detail);
            if (!parsed.primary.isEmpty()) {
                subtitle = subtitle.isEmpty() ? parsed.primary : subtitle + "  |  " + parsed.primary;
            }
            tvSubtitle.setText(subtitle);
            if (parsed.note.isEmpty()) {
                tvNote.setText("");
                tvNote.setVisibility(View.GONE);
            } else {
                tvNote.setText(parsed.note);
                tvNote.setVisibility(View.VISIBLE);
            }
        }

        private static String formatOccurredAt(String occurredAt) {
            if (occurredAt == null || occurredAt.trim().isEmpty()) {
                return "";
            }
            String raw = occurredAt.trim();
            try {
                return TIME_FMT.format(Instant.parse(raw).atZone(ZoneId.of("Asia/Shanghai")));
            } catch (Exception ignored) {
            }
            try {
                return DATE_FMT.format(LocalDate.parse(raw.substring(0, 10)));
            } catch (Exception ignored) {
            }
            return raw;
        }

        private static ParsedDetail parseDetail(String detail) {
            if (detail == null || detail.trim().isEmpty()) {
                return new ParsedDetail("", "");
            }
            String raw = detail.trim();
            int idx = raw.indexOf('|');
            if (idx < 0) {
                if (looksLikeMetric(raw)) {
                    return new ParsedDetail(raw, "");
                }
                return new ParsedDetail("", raw);
            }
            String primary = raw.substring(0, idx).trim();
            String note = raw.substring(idx + 1).trim();
            if (!looksLikeMetric(primary) && note.isEmpty()) {
                return new ParsedDetail("", primary);
            }
            return new ParsedDetail(primary, note);
        }

        private static boolean looksLikeMetric(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
            return normalized.matches("^[0-9.,]+\\s*(cents|min|minutes|h|m).*$");
        }
    }

    private static final class ParsedDetail {
        final String primary;
        final String note;

        ParsedDetail(String primary, String note) {
            this.primary = primary == null ? "" : primary;
            this.note = note == null ? "" : note;
        }
    }
}
