/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.billing.BudgetSortType.ACTUAL_COST;
import static io.harness.ccm.commons.entities.billing.BudgetSortType.BUDGET_AMOUNT;
import static io.harness.ccm.commons.entities.billing.BudgetSortType.FORECASTED_COST;
import static io.harness.ccm.commons.entities.billing.BudgetSortType.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.budgetGroup.BudgetGroup.BudgetGroupKeys;
import io.harness.ccm.commons.entities.billing.BudgetSortType;

@OwnedBy(CE)
public enum BudgetGroupSortType {
  BUDGET_GROUP_AMOUNT(BudgetGroupKeys.budgetGroupAmount, BUDGET_AMOUNT),
  BUDGET_GROUP_NAME(BudgetGroupKeys.name, NAME),
  BUDGET_GROUP_ACTUAL_COST(BudgetGroupKeys.actualCost, ACTUAL_COST),
  BUDGET_GROUP_FORECASTED_COST(BudgetGroupKeys.forecastCost, FORECASTED_COST);

  private final String columnName;
  private final BudgetSortType budgetSortType;

  BudgetGroupSortType(final String columnName, final BudgetSortType budgetSortType) {
    this.columnName = columnName;
    this.budgetSortType = budgetSortType;
  }

  public String getColumnName() {
    return columnName;
  }
  public BudgetSortType getBudgetSortType() {
    return budgetSortType;
  }
}
