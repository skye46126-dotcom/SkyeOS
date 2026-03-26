package com.example.skyeos.domain.repository;

import com.example.skyeos.domain.model.MetricSnapshotSummary;
import com.example.skyeos.domain.model.MonthlyCostBaseline;
import com.example.skyeos.domain.model.RateComparisonSummary;
import com.example.skyeos.domain.model.RecurringCostRuleSummary;
import com.example.skyeos.domain.model.CapexCostSummary;

import java.util.List;

public interface LifeOsMetricsRepository {
    MetricSnapshotSummary recomputeSnapshot(String snapshotDate, String windowType);

    MetricSnapshotSummary getSnapshot(String snapshotDate, String windowType);

    MetricSnapshotSummary getLatestSnapshot(String windowType);

    RateComparisonSummary getRateComparison(String anchorDate, String windowType);

    long getIdealHourlyRateCents();

    void setIdealHourlyRateCents(long cents);

    long getCurrentMonthBasicLivingCents();

    void setCurrentMonthBasicLivingCents(long cents);

    long getCurrentMonthFixedSubscriptionCents();

    void setCurrentMonthFixedSubscriptionCents(long cents);

    MonthlyCostBaseline getMonthlyBaseline(String month);

    void upsertMonthlyBaseline(String month, long basicLivingCents, long fixedSubscriptionCents);

    List<RecurringCostRuleSummary> listRecurringCostRules();

    void createRecurringCostRule(String name, String category, long monthlyAmountCents, boolean isNecessary,
            String startMonth, String endMonth, String note);

    void updateRecurringCostRule(String id, String name, String category, long monthlyAmountCents, boolean isNecessary,
            String startMonth, String endMonth, String note);

    void deleteRecurringCostRule(String id);

    List<CapexCostSummary> listCapexCosts();

    void createCapexCost(String name, String purchaseDate, long purchaseAmountCents, int usefulMonths,
            int residualRateBps, String note);

    void updateCapexCost(String id, String name, String purchaseDate, long purchaseAmountCents, int usefulMonths,
            int residualRateBps, String note);

    void deleteCapexCost(String id);
}
