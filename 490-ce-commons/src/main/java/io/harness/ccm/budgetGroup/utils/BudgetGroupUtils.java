/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.utils;

import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetMonthlyBreakdown;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.exception.InvalidRequestException;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BudgetGroupUtils {
  private static final String BUDGET_GROUP_NAME_EXISTS_EXCEPTION =
      "Error in creating budget group. Budget group with given name already exists";

  public static void validateBudgetGroup(BudgetGroup budgetGroup, List<BudgetGroup> existingBudgetGroups) {
    populateDefaultBudgetGroupBreakdown(budgetGroup);
    validateBudgetGroupName(budgetGroup, existingBudgetGroups);
  }

  private static void populateDefaultBudgetGroupBreakdown(BudgetGroup budgetGroup) {
    if (budgetGroup.getBudgetGroupMonthlyBreakdown() == null) {
      budgetGroup.setBudgetGroupMonthlyBreakdown(
          BudgetMonthlyBreakdown.builder().budgetBreakdown(BudgetBreakdown.YEARLY).build());
      return;
    }
    if (budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == null) {
      budgetGroup.getBudgetGroupMonthlyBreakdown().setBudgetBreakdown(BudgetBreakdown.YEARLY);
    }
  }

  private static void validateBudgetGroupName(BudgetGroup budget, List<BudgetGroup> existingBudgetGroups) {
    if (!existingBudgetGroups.isEmpty() && (!existingBudgetGroups.get(0).getUuid().equals(budget.getUuid()))) {
      throw new InvalidRequestException(BUDGET_GROUP_NAME_EXISTS_EXCEPTION);
    }
  }
}
