/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.service;

import io.harness.ccm.budget.BudgetSummary;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budgetGroup.BudgetGroup;

import java.util.List;

public interface BudgetGroupService {
  String save(BudgetGroup budgetGroup);
  void update(String uuid, String accountId, BudgetGroup budgetGroup);
  BudgetGroup get(String uuid, String accountId);
  List<BudgetGroup> list(String accountId);
  boolean delete(String uuid, String accountId);
  List<ValueDataPoint> getLastPeriodCost(
      String accountId, boolean areChildEntitiesBudgets, List<String> childEntityIds);
  List<BudgetSummary> listBudgetsAndBudgetGroupsSummary(String accountId, String id);
}
