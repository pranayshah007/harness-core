/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.service;

import static io.harness.ccm.budget.utils.BudgetUtils.MONTHS;

import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetSummary;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.BudgetGroupChildEntityDTO;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.budgetGroup.utils.BudgetGroupUtils;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetCostData;
import io.harness.ccm.commons.entities.budget.BudgetData;
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
  public static final String INVALID_BUDGET_GROUP_ID_EXCEPTION = "Invalid budget group id";
  public static final String MISSING_BUDGET_GROUP_DATA_EXCEPTION = "Missing Budget Group data exception";

  @Override
  public String save(BudgetGroup budgetGroup) {
    BudgetGroupUtils.validateBudgetGroup(
        budgetGroup, budgetGroupDao.list(budgetGroup.getAccountId(), budgetGroup.getName()));
    updateBudgetGroupHistory(budgetGroup, budgetGroup.getAccountId());
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

  @Override
  public BudgetData getBudgetGroupTimeSeriesStats(BudgetGroup budgetGroup, BudgetBreakdown breakdown) {
    if (budgetGroup == null) {
      throw new InvalidRequestException(INVALID_BUDGET_GROUP_ID_EXCEPTION);
    }

    List<BudgetCostData> budgetGroupCostDataList = new ArrayList<>();
    Double budgetedGroupAmount = budgetGroup.getBudgetGroupAmount();
    if (budgetedGroupAmount == null) {
      budgetedGroupAmount = 0.0;
    }

    if (budgetGroup.getPeriod() == BudgetPeriod.YEARLY && breakdown == BudgetBreakdown.MONTHLY) {
      Double[] actualCost = budgetGroup.getBudgetGroupMonthlyBreakdown().getActualMonthlyCost();
      if (actualCost == null || actualCost.length != MONTHS) {
        log.error("Missing monthly actualCost of yearly budget group with id:" + budgetGroup.getUuid());
        throw new InvalidRequestException(MISSING_BUDGET_GROUP_DATA_EXCEPTION);
      }

      Double[] budgetGroupAmount =
          BudgetUtils.getYearlyMonthWiseValues(budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount());
      if (budgetGroupAmount == null || budgetGroupAmount.length != MONTHS) {
        log.error("Missing monthly budgetCost of yearly budget group with id:" + budgetGroup.getUuid());
        throw new InvalidRequestException(MISSING_BUDGET_GROUP_DATA_EXCEPTION);
      }

      Double[] forecastMonthlyCost = budgetGroup.getBudgetGroupMonthlyBreakdown().getForecastMonthlyCost();
      if (forecastMonthlyCost == null || forecastMonthlyCost.length != MONTHS) {
        log.error("Missing monthly forecastCost of yearly budget group with id:" + budgetGroup.getUuid());
        throw new InvalidRequestException(MISSING_BUDGET_GROUP_DATA_EXCEPTION);
      }

      long startTime = budgetGroup.getStartTime();
      for (int month = 0; month < MONTHS; month++) {
        long endTime = BudgetUtils.getEndTimeForBudget(startTime, BudgetPeriod.MONTHLY) - BudgetUtils.ONE_DAY_MILLIS;
        double budgetGroupVariance =
            BudgetUtils.getBudgetVariance(budgetGroupAmount[month], forecastMonthlyCost[month]);
        double budgetGroupVariancePercentage =
            BudgetUtils.getBudgetVariancePercentage(budgetGroupVariance, budgetGroupAmount[month]);
        BudgetCostData budgetCostData =
            BudgetCostData.builder()
                .actualCost(BudgetUtils.getRoundedValue(actualCost[month]))
                .forecastCost(BudgetUtils.getRoundedValue(forecastMonthlyCost[month]))
                .budgeted(BudgetUtils.getRoundedValue(budgetGroupAmount[month]))
                .budgetVariance(BudgetUtils.getRoundedValue(budgetGroupVariance))
                .budgetVariancePercentage(BudgetUtils.getRoundedValue(budgetGroupVariancePercentage))
                .time(startTime)
                .endTime(endTime)
                .build();
        budgetGroupCostDataList.add(budgetCostData);
        startTime = endTime + BudgetUtils.ONE_DAY_MILLIS;
      }
    } else {
      for (BudgetCostData historyBudgetGroupCostData : budgetGroup.getBudgetGroupHistory().values()) {
        budgetGroupCostDataList.add(historyBudgetGroupCostData);
      }
      double budgetGroupAmount = budgetGroup.getBudgetGroupAmount();
      double budgetGroupVariance = BudgetUtils.getBudgetVariance(budgetGroupAmount, budgetGroup.getActualCost());
      double budgetGroupVariancePercentage =
          BudgetUtils.getBudgetVariancePercentage(budgetGroupVariance, budgetGroupAmount);
      BudgetCostData latestBudgetCostData = BudgetCostData.builder()
                                                .time(budgetGroup.getStartTime())
                                                .endTime(budgetGroup.getEndTime())
                                                .actualCost(budgetGroup.getActualCost())
                                                .forecastCost(budgetGroup.getForecastCost())
                                                .budgeted(budgetGroupAmount)
                                                .budgetVariance(budgetGroupVariance)
                                                .budgetVariancePercentage(budgetGroupVariancePercentage)
                                                .build();
      budgetGroupCostDataList.add(latestBudgetCostData);
    }
    return BudgetData.builder().costData(budgetGroupCostDataList).forecastCost(budgetGroup.getForecastCost()).build();
  }

  private void updateBudgetGroupHistory(BudgetGroup budgetGroup, String accountId) {
    HashMap<Long, BudgetCostData> budgetGroupHistory = new HashMap<>();
    for (BudgetGroupChildEntityDTO budgetGroupChildEntityDTO : budgetGroup.getChildEntities()) {
      HashMap<Long, BudgetCostData> childHistory;
      if (budgetGroupChildEntityDTO.isBudgetGroup()) {
        BudgetGroup childBudgetGroup = get(budgetGroupChildEntityDTO.getId(), accountId);
        childHistory = childBudgetGroup != null ? childBudgetGroup.getBudgetGroupHistory() : null;
      } else {
        Budget childBudget = budgetDao.get(budgetGroupChildEntityDTO.getId(), accountId);
        childHistory = childBudget != null ? childBudget.getBudgetHistory() : null;
      }
      for (Long startTime : childHistory.keySet()) {
        double actualCost;
        if (budgetGroupHistory.containsKey(startTime)) {
          actualCost = budgetGroupHistory.get(startTime).getActualCost() + childHistory.get(startTime).getActualCost();
        } else {
          actualCost = childHistory.get(startTime).getActualCost();
        }
        double budgetVariance = BudgetUtils.getBudgetVariance(budgetGroup.getBudgetGroupAmount(), actualCost);
        double budgetVariancePercentage =
            BudgetUtils.getBudgetVariancePercentage(budgetVariance, budgetGroup.getBudgetGroupAmount());
        BudgetCostData newBudgetGroupCostData = BudgetCostData.builder()
                                                    .actualCost(actualCost)
                                                    .time(startTime)
                                                    .endTime(childHistory.get(startTime).getEndTime())
                                                    .budgeted(budgetGroup.getBudgetGroupAmount())
                                                    .forecastCost(0.0)
                                                    .budgetVariance(budgetVariance)
                                                    .budgetVariancePercentage(budgetVariancePercentage)
                                                    .build();
        budgetGroupHistory.put(startTime, newBudgetGroupCostData);
      }
    }
    budgetGroup.setBudgetGroupHistory(budgetGroupHistory);
  }
}
