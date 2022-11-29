/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.budget;

import io.harness.ccm.budget.BudgetBreakdown;
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
  Double[] getActualMonthlyCost(Budget budget);
  Double[] getActualMonthlyCost(String accountId, String perspectiveId, long startOfPeriod, BudgetPeriod period);
  Double[] getForecastMonthlyCost(Budget budget);
  Double[] getForecastMonthlyCost(String accountId, String perspectiveId, long startOfPeriod, BudgetPeriod period);
  Double[] getLastYearMonthlyCost(Budget budget);
  Double[] getLastYearMonthlyCost(String accountId, String perspectiveId, long startTime, BudgetPeriod period);
  BudgetData getBudgetTimeSeriesStats(Budget budget, BudgetBreakdown breakdown);
}
