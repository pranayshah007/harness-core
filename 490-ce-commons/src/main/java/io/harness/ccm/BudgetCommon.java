/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetMonthlyBreakdown;
import io.harness.ccm.budget.BudgetPeriod;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BudgetCommon {
  String uuid;
  String accountId;
  String name;
  BudgetMonthlyBreakdown budgetMonthlyBreakdown;
  BudgetPeriod period;
  Double budgetAmount;
  Double actualCost;
  Double forecastCost;
  Double lastMonthCost;
  AlertThreshold[] alertThresholds;
  long startTime;
  boolean budgetGroup;
  String[] emailAddresses;
  String[] userGroupIds;
  boolean isNgBudget;
  boolean notifyOnSlack;
  String perspectiveName;
  String perspectiveId;
}
