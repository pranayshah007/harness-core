/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.budget;

import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetData;

import java.util.List;

public interface BudgetService {
  String create(Budget budgetRecord);
  String clone(String budgetId, String budgetName, String accountId);

  Budget get(String budgetId, String accountId);

  void update(String budgetId, Budget budget, Budget oldBudget);
  void updatePerspectiveName(String accountId, String perspectiveId, String perspectiveName);

  List<Budget> list(String accountId);
  List<Budget> list(String accountId, String viewId);

  boolean delete(String budgetId, String accountId);
  boolean deleteBudgetsForPerspective(String accountId, String perspectiveId);

  BudgetData getBudgetTimeSeriesStats(Budget budget, BudgetBreakdown breakdown);
  void updateBudgetCosts(Budget budget);
  void updateBudgetHistory(Budget budget);
}
