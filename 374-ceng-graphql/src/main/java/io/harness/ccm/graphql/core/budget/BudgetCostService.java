package io.harness.ccm.graphql.core.budget;

import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetData;

public interface BudgetCostService {
  double getActualCost(Budget budget);
  double getActualCost(String accountId, String perspectiveId, long startOfPeriod, BudgetPeriod period);
  double getForecastCost(Budget budget);
  double getForecastCost(String accountId, String perspectiveId, long startTime, BudgetPeriod period);
  double getLastPeriodCost(Budget budget);
  double getLastPeriodCost(String accountId, String perspectiveId, long startTime, BudgetPeriod period);
  BudgetData getBudgetTimeSeriesStats(Budget budget);
}
