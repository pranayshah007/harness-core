/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.budget;

import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetScope;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
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
    updateBudgetStartTime(budget);
    updateBudgetEndTime(budget);
    updateBudgetCosts(budget);
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
                             .build();
    return create(cloneBudget);
  }

  @Override
  public Budget get(String budgetId, String accountId) {
    return budgetDao.get(budgetId, accountId);
  }

  @Override
  public void update(String budgetId, Budget budget) {
    if (budget.getAccountId() == null) {
      Budget existingBudget = budgetDao.get(budgetId);
      budget.setAccountId(existingBudget.getAccountId());
    }
    if (budget.getUuid() == null) {
      budget.setUuid(budgetId);
    }
    BudgetUtils.validateBudget(budget, budgetDao.list(budget.getAccountId(), budget.getName()));
    removeEmailDuplicates(budget);
    validatePerspective(budget);
    updateBudgetEndTime(budget);
    updateBudgetCosts(budget);
    budgetDao.update(budgetId, budget);
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
    return budgetDao.delete(budgetId, accountId);
  }

  @Override
  public boolean deleteBudgetsForPerspective(String accountId, String perspectiveId) {
    List<Budget> budgets = list(accountId, perspectiveId);
    List<String> budgetIds = budgets.stream().map(Budget::getUuid).collect(Collectors.toList());
    return budgetDao.delete(budgetIds, accountId);
  }

  @Override
  public BudgetData getBudgetTimeSeriesStats(Budget budget) {
    return budgetCostService.getBudgetTimeSeriesStats(budget);
  }

  private void validatePerspective(Budget budget) {
    BudgetScope scope = budget.getScope();
    String[] entityIds = BudgetUtils.getAppliesToIds(scope);
    log.debug("entityIds is {}", entityIds);
    if (ceViewService.get(entityIds[0]) == null) {
      throw new InvalidRequestException(BudgetUtils.INVALID_ENTITY_ID_EXCEPTION);
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
    boolean isStartTimeValid = true;
    try {
      budget.setEndTime(BudgetUtils.getEndTimeForBudget(budget.getStartTime(), budget.getPeriod()));
      if (budget.getEndTime() < BudgetUtils.getStartOfCurrentDay()) {
        isStartTimeValid = false;
      }
    } catch (Exception e) {
      log.error("Error occurred while updating end time of budget: {}, Exception : {}", budget.getUuid(), e);
    }

    if (!isStartTimeValid) {
      throw new InvalidRequestException(BudgetUtils.INVALID_START_TIME_EXCEPTION);
    }
  }

  // Methods for updating costs for budget
  @Override
  public void updateBudgetCosts(Budget budget) {
    // If given budget is next-gen budget, update ng budget costs and return
    if (updateNgBudgetCosts(budget)) {
      return;
    }
    try {
      Double actualCost = getActualCostForPerspectiveBudget(budget);
      Double forecastCost = getForecastCostForPerspectiveBudget(budget);
      Double lastMonthCost = getLastMonthCostForPerspectiveBudget(budget);

      budget.setActualCost(actualCost);
      budget.setForecastCost(forecastCost);
      budget.setLastMonthCost(lastMonthCost);
    } catch (Exception e) {
      log.error("Error occurred while updating costs of budget: {}, Exception : {}", budget.getUuid(), e);
    }
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
      Double actualCost = budgetCostService.getActualCost(budget);
      Double forecastCost = budgetCostService.getForecastCost(budget);
      Double lastPeriodCost = budgetCostService.getLastPeriodCost(budget);

      budget.setActualCost(actualCost);
      budget.setForecastCost(forecastCost);
      budget.setLastMonthCost(lastPeriodCost);
      return true;
    } catch (Exception e) {
      log.error("Error occurred while updating costs of budget: {}, Exception : {}", budget.getUuid(), e);
      return false;
    }
  }
}
