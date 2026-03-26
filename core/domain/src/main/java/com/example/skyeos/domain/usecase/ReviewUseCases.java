package com.example.skyeos.domain.usecase;

import javax.inject.Inject;

import com.example.skyeos.domain.model.ReviewReport;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.repository.LifeOsReviewRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public class ReviewUseCases {

    private final LifeOsReviewRepository repository;

    @Inject
    public ReviewUseCases(LifeOsReviewRepository repository) {
        this.repository = repository;
    }

    public ReviewReport getDailyReview(LocalDate date) {
        return repository.getDailyReview(date.toString());
    }

    public ReviewReport getWeeklyReview(LocalDate dateInWeek) {
        LocalDate startOfWeek = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = dateInWeek.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return repository.getWeeklyReview(startOfWeek.toString(), endOfWeek.toString());
    }

    public ReviewReport getMonthlyReview(LocalDate dateInMonth) {
        LocalDate startOfMonth = dateInMonth.withDayOfMonth(1);
        LocalDate endOfMonth = dateInMonth.with(TemporalAdjusters.lastDayOfMonth());
        return repository.getMonthlyReview(startOfMonth.toString(), endOfMonth.toString());
    }

    public ReviewReport getYearlyReview(LocalDate dateInYear) {
        LocalDate startOfYear = dateInYear.withDayOfYear(1);
        LocalDate endOfYear = dateInYear.with(TemporalAdjusters.lastDayOfYear());
        return repository.getYearlyReview(startOfYear.toString(), endOfYear.toString());
    }

    public ReviewReport getRangeReview(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate;
        LocalDate end = endDate;
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return repository.getRangeReview(start.toString(), end.toString());
    }

    public List<RecentRecordItem> getDailyTagDetail(LocalDate date, String scope, String tagName, int limit) {
        String day = date.toString();
        return repository.getTagDetailRecords(scope, tagName, day, day, limit);
    }

    public List<RecentRecordItem> getWeeklyTagDetail(LocalDate dateInWeek, String scope, String tagName, int limit) {
        LocalDate startOfWeek = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = dateInWeek.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return repository.getTagDetailRecords(scope, tagName, startOfWeek.toString(), endOfWeek.toString(), limit);
    }

    public List<RecentRecordItem> getMonthlyTagDetail(LocalDate dateInMonth, String scope, String tagName, int limit) {
        LocalDate startOfMonth = dateInMonth.withDayOfMonth(1);
        LocalDate endOfMonth = dateInMonth.with(TemporalAdjusters.lastDayOfMonth());
        return repository.getTagDetailRecords(scope, tagName, startOfMonth.toString(), endOfMonth.toString(), limit);
    }

    public List<RecentRecordItem> getYearlyTagDetail(LocalDate dateInYear, String scope, String tagName, int limit) {
        LocalDate startOfYear = dateInYear.withDayOfYear(1);
        LocalDate endOfYear = dateInYear.with(TemporalAdjusters.lastDayOfYear());
        return repository.getTagDetailRecords(scope, tagName, startOfYear.toString(), endOfYear.toString(), limit);
    }

    public List<RecentRecordItem> getRangeTagDetail(LocalDate startDate, LocalDate endDate, String scope, String tagName,
            int limit) {
        LocalDate start = startDate;
        LocalDate end = endDate;
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return repository.getTagDetailRecords(scope, tagName, start.toString(), end.toString(), limit);
    }
}
