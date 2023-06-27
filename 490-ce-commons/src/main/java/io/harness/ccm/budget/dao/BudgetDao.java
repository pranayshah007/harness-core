/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budget.dao;

import io.harness.ccm.budget.BudgetMonthlyBreakdown;
import io.harness.ccm.budget.BudgetMonthlyBreakdown.BudgetMonthlyBreakdownKeys;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.billing.Budget.BudgetKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;

public class BudgetDao {
  @Inject private HPersistence persistence;
  private static final String SCOPE_VIEW_ID = "scope.viewId";
  private static final String SCOPE_VIEW_NAME = "scope.viewName";
  private static final String BUDGET_MONTHLY_BREAKDOWN_BUDGET_MONTHLY_AMOUNT =
      BudgetKeys.budgetMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.budgetMonthlyAmount;
  private static final String BUDGET_MONTHLY_BREAKDOWN_BUDGET_BREAKDOWN =
      BudgetKeys.budgetMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.budgetBreakdown;
  private static final String BUDGET_MONTHLY_BREAKDOWN_ACTUAL_MONTHLY_COST =
      BudgetKeys.budgetMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.actualMonthlyCost;
  private static final String BUDGET_MONTHLY_BREAKDOWN_FORECAST_MONTHLY_COST =
      BudgetKeys.budgetMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.forecastMonthlyCost;
  private static final String BUDGET_MONTHLY_BREAKDOWN_YEARLY_LAST_PERIOD_COST =
      BudgetKeys.budgetMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.yearlyLastPeriodCost;

  public String save(Budget budget) {
    return persistence.save(budget);
  }

  public List<String> save(List<Budget> budgets) {
    return persistence.save(budgets);
  }

  public Budget get(String budgetId) {
    Query<Budget> query = persistence.createQuery(Budget.class).field(BudgetKeys.uuid).equal(budgetId);
    return query.get();
  }

  public Budget get(String budgetId, String accountId) {
    Query<Budget> query = persistence.createQuery(Budget.class)
                              .field(BudgetKeys.uuid)
                              .equal(budgetId)
                              .field(BudgetKeys.accountId)
                              .equal(accountId);
    return query.get();
  }

  public List<Budget> list(String accountId) {
    return list(accountId, Integer.MAX_VALUE - 1, 0);
  }

  public List<Budget> list(String accountId, List<BudgetPeriod> budgetPeriods, Integer count, Integer startIndex) {
    Query<Budget> query = persistence.createQuery(Budget.class)
                              .field(BudgetKeys.accountId)
                              .equal(accountId)
                              .field(BudgetKeys.period)
                              .in(budgetPeriods);
    return query.asList(new FindOptions().skip(startIndex).limit(count));
  }

  public List<Budget> list(String accountId, Integer count, Integer startIndex) {
    Query<Budget> query = persistence.createQuery(Budget.class).field(BudgetKeys.accountId).equal(accountId);
    return query.asList(new FindOptions().skip(startIndex).limit(count));
  }

  public List<Budget> list(String accountId, String budgetName) {
    Query<Budget> query = persistence.createQuery(Budget.class)
                              .field(BudgetKeys.accountId)
                              .equal(accountId)
                              .field(BudgetKeys.name)
                              .equal(budgetName);
    return query.asList();
  }

  public List<Budget> list(String accountId, List<String> budgetIds) {
    Query<Budget> query = persistence.createQuery(Budget.class)
                              .field(BudgetKeys.accountId)
                              .equal(accountId)
                              .field(BudgetKeys.uuid)
                              .in(budgetIds);
    return query.asList();
  }

  // Lists Current Gen budgets
  public List<Budget> listCgBudgets(String accountId, Integer count, Integer startIndex) {
    Query<Budget> query = persistence.createQuery(Budget.class)
                              .field(BudgetKeys.accountId)
                              .equal(accountId)
                              .field(BudgetKeys.isNgBudget)
                              .equal(false);
    return query.asList(new FindOptions().skip(startIndex).limit(count));
  }

  public void update(String budgetId, Budget budget) {
    Query query = persistence.createQuery(Budget.class).field(BudgetKeys.uuid).equal(budgetId);
    UpdateOperations<Budget> updateOperations = persistence.createUpdateOperations(Budget.class)
                                                    .set(BudgetKeys.name, budget.getName())
                                                    .set(BudgetKeys.scope, budget.getScope())
                                                    .set(BudgetKeys.type, budget.getType())
                                                    .set(BudgetKeys.budgetAmount, budget.getBudgetAmount())
                                                    .set(BudgetKeys.notifyOnSlack, budget.isNotifyOnSlack())
                                                    .set(BudgetKeys.actualCost, budget.getActualCost())
                                                    .set(BudgetKeys.forecastCost, budget.getForecastCost())
                                                    .set(BudgetKeys.lastMonthCost, budget.getLastMonthCost())
                                                    .set(BudgetKeys.startTime, budget.getStartTime())
                                                    .set(BudgetKeys.endTime, budget.getEndTime());

    if (budget.getGrowthRate() != null) {
      updateOperations.set(BudgetKeys.growthRate, budget.getGrowthRate());
    }
    if (budget.getPeriod() != null) {
      updateOperations.set(BudgetKeys.period, budget.getPeriod());
    }
    if (null != budget.getAlertThresholds()) {
      updateOperations.set(BudgetKeys.alertThresholds, budget.getAlertThresholds());
    }
    if (null != budget.getEmailAddresses()) {
      updateOperations.set(BudgetKeys.emailAddresses, budget.getEmailAddresses());
    }
    if (null != budget.getUserGroupIds()) {
      updateOperations.set(BudgetKeys.userGroupIds, budget.getUserGroupIds());
    }
    if (null != budget.getBudgetMonthlyBreakdown() && null != budget.getBudgetMonthlyBreakdown().getBudgetBreakdown()) {
      updateOperations.set(
          BUDGET_MONTHLY_BREAKDOWN_BUDGET_BREAKDOWN, budget.getBudgetMonthlyBreakdown().getBudgetBreakdown());
    }
    if (null != budget.getBudgetMonthlyBreakdown()
        && null != budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount()) {
      updateOperations.set(
          BUDGET_MONTHLY_BREAKDOWN_BUDGET_MONTHLY_AMOUNT, budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount());
    }
    if (null != budget.getBudgetMonthlyBreakdown()
        && null != budget.getBudgetMonthlyBreakdown().getActualMonthlyCost()) {
      updateOperations.set(
          BUDGET_MONTHLY_BREAKDOWN_ACTUAL_MONTHLY_COST, budget.getBudgetMonthlyBreakdown().getActualMonthlyCost());
    }
    if (null != budget.getBudgetMonthlyBreakdown()
        && null != budget.getBudgetMonthlyBreakdown().getForecastMonthlyCost()) {
      updateOperations.set(
          BUDGET_MONTHLY_BREAKDOWN_FORECAST_MONTHLY_COST, budget.getBudgetMonthlyBreakdown().getForecastMonthlyCost());
    }
    if (null != budget.getBudgetMonthlyBreakdown()
        && null != budget.getBudgetMonthlyBreakdown().getYearlyLastPeriodCost()) {
      updateOperations.set(BUDGET_MONTHLY_BREAKDOWN_YEARLY_LAST_PERIOD_COST,
          budget.getBudgetMonthlyBreakdown().getYearlyLastPeriodCost());
    }
    if (null != budget.getBudgetHistory()) {
      updateOperations.set(BudgetKeys.budgetHistory, budget.getBudgetHistory());
    }
    if (null != budget.getDisableCurrencyWarning()) {
      updateOperations.set(BudgetKeys.disableCurrencyWarning, budget.getDisableCurrencyWarning());
    }
    persistence.update(query, updateOperations);
  }

  public void updateBudgetMonthlyBreakdown(String budgetId, BudgetMonthlyBreakdown budgetMonthlyBreakdown) {
    Query query = persistence.createQuery(Budget.class).field(BudgetKeys.uuid).equal(budgetId);
    UpdateOperations<Budget> updateOperations = persistence.createUpdateOperations(Budget.class);

    updateOperations.set(BudgetKeys.budgetMonthlyBreakdown, budgetMonthlyBreakdown);
    persistence.update(query, updateOperations);
  }

  public void updatePerspectiveName(String accountId, String perspectiveId, String perspectiveName) {
    Query query = persistence.createQuery(Budget.class)
                      .disableValidation()
                      .field(BudgetKeys.accountId)
                      .equal(accountId)
                      .field(SCOPE_VIEW_ID)
                      .equal(perspectiveId);
    UpdateOperations<Budget> updateOperations =
        persistence.createUpdateOperations(Budget.class).disableValidation().set(SCOPE_VIEW_NAME, perspectiveName);
    persistence.update(query, updateOperations);
  }

  public void updateBudgetAmount(String budgetId, Double budgetAmount) {
    Query<Budget> query =
        persistence.createQuery(Budget.class).disableValidation().field(BudgetKeys.uuid).equal(budgetId);
    UpdateOperations<Budget> updateOperations =
        persistence.createUpdateOperations(Budget.class).set(BudgetKeys.budgetAmount, budgetAmount);
    persistence.update(query, updateOperations);
  }

  public void updateBudgetAmountInBreakdown(String budgetId, List<ValueDataPoint> monthlyBudgetAmounts) {
    Query<Budget> query =
        persistence.createQuery(Budget.class).disableValidation().field(BudgetKeys.uuid).equal(budgetId);
    UpdateOperations<Budget> updateOperations =
        persistence.createUpdateOperations(Budget.class)
            .set(BUDGET_MONTHLY_BREAKDOWN_BUDGET_MONTHLY_AMOUNT, monthlyBudgetAmounts);
    persistence.update(query, updateOperations);
  }

  public void updateParentId(String parentId, List<String> budgetIds) {
    Query<Budget> query = persistence.createQuery(Budget.class).field(BudgetKeys.uuid).in(budgetIds);
    UpdateOperations<Budget> updateOperations = parentId != null
        ? persistence.createUpdateOperations(Budget.class).set(BudgetKeys.parentBudgetGroupId, parentId)
        : persistence.createUpdateOperations(Budget.class).unset(BudgetKeys.parentBudgetGroupId);
    persistence.update(query, updateOperations);
  }

  public void unsetParent(List<String> budgetIds) {
    Query<Budget> query = persistence.createQuery(Budget.class).field(BudgetKeys.uuid).in(budgetIds);
    UpdateOperations<Budget> updateOperations =
        persistence.createUpdateOperations(Budget.class).unset(BudgetKeys.parentBudgetGroupId);
    persistence.update(query, updateOperations);
  }

  public boolean delete(String budgetId, String accountId) {
    Budget budget = get(budgetId, accountId);
    if (budget != null) {
      return persistence.delete(Budget.class, budgetId);
    }
    return false;
  }

  public boolean delete(List<String> budgetIds, String accountId) {
    Query query = persistence.createQuery(Budget.class)
                      .field(BudgetKeys.accountId)
                      .equal(accountId)
                      .field(BudgetKeys.uuid)
                      .in(budgetIds);
    return persistence.delete(query);
  }
}
