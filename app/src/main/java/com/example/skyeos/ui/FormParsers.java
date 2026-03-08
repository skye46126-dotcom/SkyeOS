package com.example.skyeos.ui;

import android.text.TextUtils;

import com.example.skyeos.domain.model.ProjectAllocation;

import java.util.ArrayList;
import java.util.List;

public final class FormParsers {
    private FormParsers() {}

    public static long parseLong(String value, long fallback) {
        if (TextUtils.isEmpty(value) || TextUtils.isEmpty(value.trim())) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static int parseInt(String value, int fallback) {
        if (TextUtils.isEmpty(value) || TextUtils.isEmpty(value.trim())) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static List<ProjectAllocation> parseAllocations(String text) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) {
            return null;
        }
        String[] chunks = text.split(",");
        List<ProjectAllocation> result = new ArrayList<>();
        for (String chunk : chunks) {
            if (TextUtils.isEmpty(chunk) || TextUtils.isEmpty(chunk.trim())) {
                continue;
            }
            String[] pair = chunk.trim().split(":");
            String projectId = pair[0].trim();
            if (TextUtils.isEmpty(projectId)) {
                continue;
            }
            double ratio = 1.0;
            if (pair.length > 1) {
                try {
                    ratio = Double.parseDouble(pair[1].trim());
                } catch (Exception ignored) {
                    ratio = 1.0;
                }
            }
            result.add(new ProjectAllocation(projectId, ratio));
        }
        return result.isEmpty() ? null : result;
    }
}

