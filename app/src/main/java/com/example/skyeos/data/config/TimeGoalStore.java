package com.example.skyeos.data.config;

import android.content.Context;
import android.content.SharedPreferences;

public final class TimeGoalStore {
    private static final String PREF = "lifeos_time_goal";
    private static final String KEY_MIN_WORK_MINUTES = "min_work_minutes";
    private static final String KEY_MIN_LEARNING_MINUTES = "min_learning_minutes";

    private final SharedPreferences preferences;

    public TimeGoalStore(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public Goal load() {
        int minWorkMinutes = Math.max(0, preferences.getInt(KEY_MIN_WORK_MINUTES, 0));
        int minLearningMinutes = Math.max(0, preferences.getInt(KEY_MIN_LEARNING_MINUTES, 0));
        return new Goal(minWorkMinutes, minLearningMinutes);
    }

    public void save(int minWorkMinutes, int minLearningMinutes) {
        preferences.edit()
                .putInt(KEY_MIN_WORK_MINUTES, Math.max(0, minWorkMinutes))
                .putInt(KEY_MIN_LEARNING_MINUTES, Math.max(0, minLearningMinutes))
                .apply();
    }

    public static final class Goal {
        public final int minWorkMinutes;
        public final int minLearningMinutes;

        public Goal(int minWorkMinutes, int minLearningMinutes) {
            this.minWorkMinutes = Math.max(0, minWorkMinutes);
            this.minLearningMinutes = Math.max(0, minLearningMinutes);
        }

        public boolean isConfigured() {
            return minWorkMinutes > 0 || minLearningMinutes > 0;
        }

        public boolean isReached(long workMinutes, long learningMinutes) {
            return workMinutes >= minWorkMinutes && learningMinutes >= minLearningMinutes;
        }
    }
}
