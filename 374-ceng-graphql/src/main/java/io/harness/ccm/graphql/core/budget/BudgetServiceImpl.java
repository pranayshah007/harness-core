/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.budget;

import static io.harness.ccm.budget.BudgetBreakdown.MONTHLY;

import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetScope;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.BudgetGroupChildEntityDTO;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.budgetGroup.service.BudgetGroupService;
import io.harness.ccm.budgetGroup.utils.BudgetGroupUtils;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetData;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.graphql.core.perspectives.PerspectiveTimeSeriesHelper;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public class BudgetServiceImpl implements BudgetService {
  @Inject private BudgetDao budgetDao;
  @Inject private BudgetGroupDao budgetGroupDao;
  @Inject private BudgetGroupService budgetGroupService;
  @Inject private CEViewService ceViewService;
  @Inject ViewsBillingService viewsBillingService;
  @Inject ViewsQueryHelper viewsQueryHelper;
  @Inject PerspectiveTimeSeriesHelper perspectiveTimeSeriesHelper;
  @Inject BigQueryService bigQueryService;
  @Inject BigQueryHelper bigQueryHelper;
  @Inject BudgetCostService budgetCostService;

  @Override
  public String create(Budget budget) {
    BudgetUtils.validateBudget(budget, budgetDao.list(budget.getAccountId(), budget.getName()));
    removeEmailDuplicates(budget);
    validatePerspective(budget);
    budget.setParentBudgetGroupId(null);
    updateBudgetStartTime(budget);
    updateBudgetEndTime(budget);
    updateBudgetCosts(budget);
    updateBudgetHistory(budget);
    return budgetDao.save(budget);
  }

  @Override
  public String clone(String budgetId, String cloneBudgetName, String accountId) {
    Budget budget = budgetDao.get(budgetId, accountId);
    BudgetUtils.validateBudget(budget, budgetDao.list(budget.getAccountId(), budget.getName()));
    BudgetUtils.validateCloneBudgetName(cloneBudgetName);
    Budget cloneBudget = Budget.builder()
                             .accountId(budget.getAccountId())
                             .name(cloneBudgetName)
                             .scope(budget.getScope())
                             .type(budget.getType())
                             .budgetAmount(budget.getBudgetAmount())
                             .period(budget.getPeriod())
                             .growthRate(budget.getGrowthRate())
                             .actualCost(budget.getActualCost())
                             .forecastCost(budget.getForecastCost())
                             .lastMonthCost(budget.getLastMonthCost())
                             .alertThresholds(budget.getAlertThresholds())
                             .userGroupIds(budget.getUserGroupIds())
                             .emailAddresses(budget.getEmailAddresses())
                             .notifyOnSlack(budget.isNotifyOnSlack())
                             .isNgBudget(budget.isNgBudget())
                             .startTime(budget.getStartTime())
                             .endTime(budget.getEndTime())
                             .budgetMonthlyBreakdown(budget.getBudgetMonthlyBreakdown())
                             .budgetHistory(budget.getBudgetHistory())
                             .build();
    return create(cloneBudget);
  }

  @Override
  public Budget get(String budgetId, String accountId) {
    return budgetDao.get(budgetId, accountId);
  }

  @Override
  public void update(String budgetId, Budget budget) {
    Budget oldBudget = budgetDao.get(budgetId);
    if (budget.getAccountId() == null) {
      budget.setAccountId(oldBudget.getAccountId());
    }
    if (budget.getUuid() == null) {
      budget.setUuid(budgetId);
    }
    BudgetUtils.validateBudget(budget, budgetDao.list(budget.getAccountId(), budget.getName()));
    removeEmailDuplicates(budget);
    validatePerspective(budget);
    updateBudgetParent(budget, oldBudget);
    updateBudgetDetails(budget, oldBudget);
    updateBudgetEndTime(budget);
    updateBudgetCosts(budget);
    budgetDao.update(budgetId, budget);
    if (budget.getParentBudgetGroupId() != null && budget.getBudgetAmount() != oldBudget.getBudgetAmount()) {
      upwardCascadeBudgetAmount(budget, oldBudget);
    }
  }

  @Override
  public void updatePerspectiveName(String accountId, String perspectiveId, String perspectiveName) {
    budgetDao.updatePerspectiveName(accountId, perspectiveId, perspectiveName);
  }

  @Override
  public List<Budget> list(String accountId) {
    return budgetDao.list(accountId, Integer.MAX_VALUE - 1, 0);
  }

  @Override
  public List<Budget> list(String accountId, String perspectiveId) {
    List<Budget> budgets = budgetDao.list(accountId, Integer.MAX_VALUE - 1, 0);
    return budgets.stream()
        .filter(budget -> isBudgetBasedOnGivenPerspective(budget, perspectiveId))
        .collect(Collectors.toList());
  }

  @Override
  public boolean delete(String budgetId, String accountId) {
    Budget budget = budgetDao.get(budgetId, accountId);
    if (budget.getParentBudgetGroupId() != null) {
      BudgetGroup parentBudgetGroup = budgetGroupDao.get(budget.getParentBudgetGroupId(), accountId);
      if (parentBudgetGroup.getChildEntities() != null && parentBudgetGroup.getChildEntities().size() > 1) {
        BudgetGroupChildEntityDTO deletedChildEntity = parentBudgetGroup.getChildEntities()
                                                           .stream()
                                                           .filter(childEntity -> childEntity.getId().equals(budgetId))
                                                           .collect(Collectors.toList())
                                                           .get(0);
        parentBudgetGroup = budgetGroupService.updateProportionsOnDeletion(deletedChildEntity, parentBudgetGroup);
        parentBudgetGroup = BudgetGroupUtils.updateBudgetGroupAmountOnChildEntityDeletion(parentBudgetGroup, budget);
        budgetGroupService.updateCostsOfParentBudgetGroupsOnEntityDeletion(parentBudgetGroup);
        BudgetGroup rootBudgetGroup = getRootBudgetGroup(budget);
        budgetGroupService.cascadeBudgetGroupAmount(rootBudgetGroup);
      } else {
        budgetGroupService.delete(budget.getParentBudgetGroupId(), accountId);
      }
    }
    return budgetDao.delete(budgetId, accountId);
  }

  @Override
  public boolean deleteBudgetsForPerspective(String accountId, String perspectiveId) {
    List<Budget> budgets = list(accountId, perspectiveId);
    for (Budget budget : budgets) {
      if (budget.getParentBudgetGroupId() != null) {
        BudgetGroup parentBudgetGroup = budgetGroupDao.get(budget.getParentBudgetGroupId(), accountId);
        if (parentBudgetGroup.getChildEntities() != null && parentBudgetGroup.getChildEntities().size() > 1) {
          BudgetGroupChildEntityDTO deletedChildEntity =
              parentBudgetGroup.getChildEntities()
                  .stream()
                  .filter(childEntity -> childEntity.getId().equals(budget.getUuid()))
                  .collect(Collectors.toList())
                  .get(0);
          parentBudgetGroup = budgetGroupService.updateProportionsOnDeletion(deletedChildEntity, parentBudgetGroup);
          parentBudgetGroup = BudgetGroupUtils.updateBudgetGroupAmountOnChildEntityDeletion(parentBudgetGroup, budget);
          budgetGroupService.updateCostsOfParentBudgetGroupsOnEntityDeletion(parentBudgetGroup);
          BudgetGroup rootBudgetGroup = getRootBudgetGroup(budget);
          budgetGroupService.cascadeBudgetGroupAmount(rootBudgetGroup);
        } else {
          budgetGroupService.delete(budget.getParentBudgetGroupId(), accountId);
        }
      }
    }
    List<String> budgetIds = budgets.stream().map(Budget::getUuid).collect(Collectors.toList());
    return budgetDao.delete(budgetIds, accountId);
  }

  @Override
  public BudgetData getBudgetTimeSeriesStats(Budget budget, BudgetBreakdown breakdown) {
    return budgetCostService.getBudgetTimeSeriesStats(budget, breakdown);
  }

  private void validatePerspective(Budget budget) {
    BudgetScope scope = budget.getScope();
    String[] entityIds = BudgetUtils.getAppliesToIds(scope);
    log.debug("entityIds is {}", entityIds);
    if (ceViewService.get(entityIds[0]) == null) {
      throw new InvalidRequestException(BudgetUtils.INVALID_ENTITY_ID_EXCEPTION);
    }
  }

  private void updateBudgetParent(Budget budget, Budget oldBudget) {
    budget.setParentBudgetGroupId(oldBudget.getParentBudgetGroupId());
    if (budget.getParentBudgetGroupId() != null) {
      BudgetGroup parentBudgetGroup = budgetGroupDao.get(budget.getParentBudgetGroupId(), budget.getAccountId());
      if (parentBudgetGroup == null) {
        budget.setParentBudgetGroupId(null);
      }
    }
  }

  private void updateBudgetDetails(Budget budget, Budget oldBudget) {
    // We do not allow updates to period or startTime of a budget
    budget.setPeriod(oldBudget.getPeriod());
    budget.setStartTime(oldBudget.getStartTime());
    budget.setEndTime(oldBudget.getEndTime());

    // In case this budget is part of budget group
    // We do not allow updates to breakdown as well
    if (budget.getParentBudgetGroupId() != null) {
      budget.getBudgetMonthlyBreakdown().setBudgetBreakdown(oldBudget.getBudgetMonthlyBreakdown().getBudgetBreakdown());
    }
  }

  public boolean isBudgetBasedOnGivenPerspective(Budget budget, String perspectiveId) {
    return budget.getScope().getEntityIds().get(0).equals(perspectiveId);
  }

  private void removeEmailDuplicates(Budget budget) {
    String[] emailAddresses = ArrayUtils.nullToEmpty(budget.getEmailAddresses());
    String[] uniqueEmailAddresses = new HashSet<>(Arrays.asList(emailAddresses)).toArray(new String[0]);
    budget.setEmailAddresses(uniqueEmailAddresses);
    // In NG we have per alertThreshold separate email addresses
    AlertThreshold[] alertThresholds = budget.getAlertThresholds();
    if (alertThresholds != null && alertThresholds.length > 0) {
      for (AlertThreshold alertThreshold : alertThresholds) {
        emailAddresses = ArrayUtils.nullToEmpty(alertThreshold.getEmailAddresses());
        uniqueEmailAddresses = new HashSet<>(Arrays.asList(emailAddresses)).toArray(new String[0]);
        alertThreshold.setEmailAddresses(uniqueEmailAddresses);
      }
      budget.setAlertThresholds(alertThresholds);
    }
  }

  private void updateBudgetStartTime(Budget budget) {
    try {
      budget.setStartTime(BudgetUtils.getStartOfDay(budget.getStartTime()));
    } catch (Exception e) {
      log.error("Error occurred while updating start time of budget: {}", budget.getUuid(), e);
    }
  }

  private void updateBudgetEndTime(Budget budget) {
    try {
      budget.setEndTime(BudgetUtils.getEndTimeForBudget(budget.getStartTime(), budget.getPeriod()));
      if (budget.getEndTime() < BudgetUtils.getStartOfCurrentDay()) {
        long timeDiff = BudgetUtils.getStartOfCurrentDay() - budget.getEndTime() + BudgetUtils.ONE_DAY_MILLIS;
        long periodInMilliSeconds = BudgetUtils.getEndTimeForBudget(0l, budget.getPeriod());
        // Calculate the number of periods needed to cover the time difference
        long periods_needed = Math.round(Math.ceil((double) timeDiff / (double) periodInMilliSeconds));
        // Calculate the total time to shift the start time
        long shift_time = periods_needed * periodInMilliSeconds;
        budget.setStartTime(budget.getStartTime() + shift_time);
        budget.setEndTime(BudgetUtils.getEndTimeForBudget(budget.getStartTime(), budget.getPeriod()));
      }
    } catch (Exception e) {
      log.error("Error occurred while updating end time of budget: {}, Exception : {}", budget.getUuid(), e);
    }
  }

  // Methods for updating costs for budget
  @Override
  public void updateBudgetCosts(Budget budget) {
    // If given budget is next-gen budget, update ng budget costs and return
    if (updateNgBudgetCosts(budget)) {
      return;
    }
    double actualCost = 0.0D;
    double forecastCost = 0.0D;
    double lastMonthCost = 0.0D;
    try {
      actualCost = getActualCostForPerspectiveBudget(budget);
      forecastCost = getForecastCostForPerspectiveBudget(budget);
      lastMonthCost = getLastMonthCostForPerspectiveBudget(budget);
    } catch (Exception e) {
      log.error("Error occurred while updating costs of budget: {}, Exception : {}", budget.getUuid(), e);
    }
    budget.setActualCost(actualCost);
    budget.setForecastCost(forecastCost);
    budget.setLastMonthCost(lastMonthCost);
  }

  @Override
  public void updateBudgetHistory(Budget budget) {
    budget.setBudgetHistory(budgetCostService.getBudgetHistory(budget));
  }

  private double getActualCostForPerspectiveBudget(Budget budget) {
    return ceViewService.getActualCostForPerspectiveBudget(
        budget.getAccountId(), budget.getScope().getEntityIds().get(0));
  }

  private double getLastMonthCostForPerspectiveBudget(Budget budget) {
    return ceViewService.getLastMonthCostForPerspective(budget.getAccountId(), budget.getScope().getEntityIds().get(0));
  }

  private double getForecastCostForPerspectiveBudget(Budget budget) {
    return ceViewService.getForecastCostForPerspective(budget.getAccountId(), budget.getScope().getEntityIds().get(0));
  }

  private boolean updateNgBudgetCosts(Budget budget) {
    try {
      if (budget.getPeriod() == BudgetPeriod.YEARLY && budget.getBudgetMonthlyBreakdown() != null
          && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == MONTHLY) {
        Double[] lastPeriodCost = budgetCostService.getLastYearMonthlyCost(budget);
        budget.getBudgetMonthlyBreakdown().setYearlyLastPeriodCost(lastPeriodCost);
        budget.setLastMonthCost(sumOfMonthlyCost(lastPeriodCost));

        Double[] actualCost = budgetCostService.getActualMonthlyCost(budget);
        budget.getBudgetMonthlyBreakdown().setActualMonthlyCost(actualCost);
        budget.setActualCost(sumOfMonthlyCost(actualCost));

        Double[] forecastCost = budgetCostService.getForecastMonthlyCost(budget);
        budget.getBudgetMonthlyBreakdown().setForecastMonthlyCost(forecastCost);
        budget.setForecastCost(sumOfMonthlyCost(forecastCost));
      } else {
        Double actualCost = budgetCostService.getActualCost(budget);
        Double forecastCost = budgetCostService.getForecastCost(budget);
        Double lastPeriodCost = budgetCostService.getLastPeriodCost(budget);
        budget.setActualCost(actualCost);
        budget.setForecastCost(forecastCost);
        budget.setLastMonthCost(lastPeriodCost);
      }
      return true;
    } catch (Exception e) {
      log.error("Error occurred while updating costs of budget: {}, Exception : {}", budget.getUuid(), e);
      return false;
    }
  }

  private Double sumOfMonthlyCost(Double monthlyCost[]) {
    Double totalCost = 0.0;
    if (monthlyCost != null) {
      totalCost += Arrays.stream(monthlyCost).reduce(0.0, (a, b) -> a + b);
    }
    return totalCost;
  }

  public BudgetGroup getRootBudgetGroup(Budget budget) {
    BudgetGroup rootBudgetGroup = budgetGroupDao.get(budget.getParentBudgetGroupId(), budget.getAccountId());
    while (rootBudgetGroup.getParentBudgetGroupId() != null) {
      rootBudgetGroup = budgetGroupDao.get(rootBudgetGroup.getParentBudgetGroupId(), rootBudgetGroup.getAccountId());
    }
    return rootBudgetGroup;
  }

  private void upwardCascadeBudgetAmount(Budget budget, Budget oldBudget) {
    BudgetGroup parentBudgetGroup = budgetGroupDao.get(budget.getParentBudgetGroupId(), budget.getAccountId());
    Double amountDiff = budget.getBudgetAmount() - oldBudget.getBudgetAmount();
    Double[] amountMonthlyDiff = null;
    Boolean isMonthlyBreadownBudget = false;
    if (budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == MONTHLY) {
      isMonthlyBreadownBudget = true;
    }
    if (isMonthlyBreadownBudget) {
      amountMonthlyDiff =
          BudgetUtils.getBudgetAmountMonthlyDifference(budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount(),
              oldBudget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount());
    }
    budgetGroupService.upwardCascadeBudgetGroupAmount(
        parentBudgetGroup, isMonthlyBreadownBudget, amountDiff, amountMonthlyDiff);
  }
}
