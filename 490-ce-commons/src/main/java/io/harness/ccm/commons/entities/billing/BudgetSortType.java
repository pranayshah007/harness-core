/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.Budget.BudgetKeys;

@OwnedBy(CE)
public enum BudgetSortType {
  BUDGET_AMOUNT(BudgetKeys.budgetAmount),
  NAME(BudgetKeys.name);

  private final String columnName;

  BudgetSortType(final String columnName) {
    this.columnName = columnName;
  }

  public String getColumnName() {
    return columnName;
  }
}
