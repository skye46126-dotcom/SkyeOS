package com.example.skyeos.ui.util;

import com.example.skyeos.R;

public class CategoryIconHelper {

    public static int getIconRes(String typeOrEmoji) {
        if (typeOrEmoji == null)
            return R.drawable.ic_nav_today;

        String clean = typeOrEmoji.toLowerCase().trim();

        // Match by type
        if (clean.contains("time"))
            return R.drawable.ic_cat_time;
        if (clean.contains("income"))
            return R.drawable.ic_cat_income;
        if (clean.contains("expense"))
            return R.drawable.ic_cat_expense;
        if (clean.contains("learning"))
            return R.drawable.ic_cat_learning;
        if (clean.contains("project"))
            return R.drawable.ic_cat_project;

        // Match by common emojis (backward compatibility)
        if (clean.contains("⏱"))
            return R.drawable.ic_cat_time;
        if (clean.contains("💰"))
            return R.drawable.ic_cat_income;
        if (clean.contains("💸"))
            return R.drawable.ic_cat_expense;
        if (clean.contains("📚"))
            return R.drawable.ic_cat_learning;

        // Default
        return R.drawable.ic_nav_today;
    }

    public static int getCategoryColor(String type) {
        if (type == null)
            return 0xFF1E56D9;
        switch (type.toLowerCase()) {
            case "time":
                return 0xFF3B82F6;
            case "income":
                return 0xFF10B981;
            case "expense":
                return 0xFFF59E0B;
            case "learning":
                return 0xFF4F8BFF;
            case "project":
                return 0xFFEF4444;
            default:
                return 0xFF1E56D9;
        }
    }
}
