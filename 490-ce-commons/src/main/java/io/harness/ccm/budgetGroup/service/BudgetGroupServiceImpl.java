/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.service;

import io.harness.ccm.budget.BudgetSummary;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.budgetGroup.utils.BudgetGroupUtils;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BudgetGroupServiceImpl implements BudgetGroupService {
  @Inject BudgetGroupDao budgetGroupDao;
  @Inject BudgetDao budgetDao;

  @Override
  public String save(BudgetGroup budgetGroup) {
    BudgetGroupUtils.validateBudgetGroup(
        budgetGroup, budgetGroupDao.list(budgetGroup.getAccountId(), budgetGroup.getName()));
    return budgetGroupDao.save(budgetGroup);
  }

  @Override
  public void update(String uuid, String accountId, BudgetGroup budgetGroup) {
    BudgetGroupUtils.validateBudgetGroup(
        budgetGroup, budgetGroupDao.list(budgetGroup.getAccountId(), budgetGroup.getName()));
    budgetGroupDao.update(uuid, accountId, budgetGroup);
  }

  @Override
  public BudgetGroup get(String uuid, String accountId) {
    return budgetGroupDao.get(uuid, accountId);
  }

  @Override
  public List<BudgetGroup> list(String accountId) {
    return budgetGroupDao.list(accountId, Integer.MAX_VALUE, 0);
  }

  @Override
  public boolean delete(String uuid, String accountId) {
    return budgetGroupDao.delete(uuid, accountId);
  }

  @Override
  public List<ValueDataPoint> getLastPeriodCost(
      String accountId, boolean areChildEntitiesBudgets, List<String> childEntityIds) {
    if (areChildEntitiesBudgets) {
      List<Budget> childBudgets = budgetDao.list(accountId, childEntityIds);
      if (childBudgets == null || childBudgets.size() != childEntityIds.size()) {
        throw new InvalidRequestException(BudgetGroupUtils.INVALID_CHILD_ENTITY_ID_EXCEPTION);
      }
      return BudgetGroupUtils.getLastPeriodCostOfChildBudgets(childBudgets);
    } else {
      List<BudgetGroup> childBudgetGroups = budgetGroupDao.list(accountId, childEntityIds);
      if (childBudgetGroups == null || childBudgetGroups.size() != childEntityIds.size()) {
        throw new InvalidRequestException(BudgetGroupUtils.INVALID_CHILD_ENTITY_ID_EXCEPTION);
      }
      return BudgetGroupUtils.getLastPeriodCostOfChildBudgetGroups(childBudgetGroups);
    }
  }

  @Override
  public List<BudgetSummary> listBudgetsAndBudgetGroupsSummary(String accountId, String id) {
    List<BudgetSummary> summaryList = new ArrayList<>();

    List<BudgetGroup> budgetGroups = list(accountId);
    final Map<String, BudgetGroup> budgetGroupIdMapping =
        budgetGroups.stream().collect(Collectors.toMap(BudgetGroup::getUuid, budgetGroup -> budgetGroup));
    budgetGroups.sort(Comparator.comparing(BudgetGroup::getLastUpdatedAt).reversed());

    List<Budget> budgets = budgetDao.list(accountId);
    budgets = budgets.stream().filter(BudgetUtils::isPerspectiveBudget).collect(Collectors.toList());
    budgets.sort(Comparator.comparing(Budget::getLastUpdatedAt).reversed());
    List<Budget> budgetsPartOfBudgetGroups =
        budgets.stream().filter(budget -> budget.getParentBudgetGroupId() != null).collect(Collectors.toList());
    List<Budget> budgetsNotPartOfBudgetGroups =
        budgets.stream().filter(budget -> budget.getParentBudgetGroupId() == null).collect(Collectors.toList());

    if (budgetsPartOfBudgetGroups.size() != 0) {
      Map<String, List<BudgetSummary>> childEntitySummaryMapping = new HashMap<>();
      List<String> parentBudgetGroupIds = new ArrayList<>();

      for (Budget budget : budgetsPartOfBudgetGroups) {
        String parentBudgetGroupId = budget.getParentBudgetGroupId();
        parentBudgetGroupIds.add(parentBudgetGroupId);
        if (childEntitySummaryMapping.containsKey(parentBudgetGroupId)) {
          childEntitySummaryMapping.get(parentBudgetGroupId).add(BudgetUtils.buildBudgetSummary(budget));
        } else {
          List<BudgetSummary> childEntitySummary = new ArrayList<>();
          childEntitySummary.add(BudgetUtils.buildBudgetSummary(budget));
          childEntitySummaryMapping.put(parentBudgetGroupId, childEntitySummary);
        }
      }

      while (parentBudgetGroupIds.size() != 0) {
        if (id != null && childEntitySummaryMapping.containsKey(id)) {
          return childEntitySummaryMapping.get(id);
        }
        List<String> newParentBudgetGroupIds = new ArrayList<>();
        parentBudgetGroupIds.forEach(budgetGroupId -> {
          BudgetGroup budgetGroup = budgetGroupIdMapping.get(budgetGroupId);
          String parentBudgetGroupId = budgetGroup.getParentBudgetGroupId();
          if (parentBudgetGroupId != null) {
            newParentBudgetGroupIds.add(parentBudgetGroupId);
            if (childEntitySummaryMapping.containsKey(parentBudgetGroupId)) {
              childEntitySummaryMapping.get(parentBudgetGroupId)
                  .add(BudgetGroupUtils.buildBudgetGroupSummary(
                      budgetGroup, childEntitySummaryMapping.get(budgetGroupId)));
            } else {
              List<BudgetSummary> childEntitySummary = new ArrayList<>();
              childEntitySummary.add(
                  BudgetGroupUtils.buildBudgetGroupSummary(budgetGroup, childEntitySummaryMapping.get(budgetGroupId)));
              childEntitySummaryMapping.put(parentBudgetGroupId, childEntitySummary);
            }
          } else {
            summaryList.add(
                BudgetGroupUtils.buildBudgetGroupSummary(budgetGroup, childEntitySummaryMapping.get(budgetGroupId)));
          }
        });
        parentBudgetGroupIds = newParentBudgetGroupIds;
      }
    }

    budgetsNotPartOfBudgetGroups.forEach(budget -> summaryList.add(BudgetUtils.buildBudgetSummary(budget)));
    return summaryList;
  }
}
