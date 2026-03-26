package com.example.skyeos.domain.repository;

import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.ReviewReport;

import java.util.List;

public interface LifeOsReviewRepository {

    /**
     * Gets a review report for a specific day.
     * 
     * @param date iso date yyyy-MM-dd
     */
    ReviewReport getDailyReview(String date);

    /**
     * Gets a review report for a specific week.
     * 
     * @param weekStart iso date yyyy-MM-dd
     * @param weekEnd   iso date yyyy-MM-dd
     */
    ReviewReport getWeeklyReview(String weekStart, String weekEnd);

    /**
     * Gets a review report for a specific month.
     * 
     * @param monthStart iso date yyyy-MM-dd
     * @param monthEnd   iso date yyyy-MM-dd
     */
    ReviewReport getMonthlyReview(String monthStart, String monthEnd);

    /**
     * Gets a review report for a specific year.
     *
     * @param yearStart iso date yyyy-MM-dd
     * @param yearEnd   iso date yyyy-MM-dd
     */
    ReviewReport getYearlyReview(String yearStart, String yearEnd);

    ReviewReport getRangeReview(String startDate, String endDate);

    List<RecentRecordItem> getTagDetailRecords(String scope, String tagName, String startDate, String endDate, int limit);
}
