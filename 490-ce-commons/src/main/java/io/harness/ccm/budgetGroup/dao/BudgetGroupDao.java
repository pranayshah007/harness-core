/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.dao;

import io.harness.ccm.budget.BudgetMonthlyBreakdown.BudgetMonthlyBreakdownKeys;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.BudgetGroup.BudgetGroupKeys;
import io.harness.ccm.budgetGroup.BudgetGroupChildEntityDTO;
import io.harness.ccm.budgetGroup.BudgetGroupSortType;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.client.model.Collation;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Objects;

public class BudgetGroupDao {
  @Inject private HPersistence hPersistence;

  private static final String BUDGET_GROUP_MONTHLY_BREAKDOWN_MONTHLY_AMOUNTS =
      BudgetGroupKeys.budgetGroupMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.budgetMonthlyAmount;
  private static final String BUDGET_GROUP_MONTHLY_BREAKDOWN_ACTUAL_MONTHLY_COST =
      BudgetGroupKeys.budgetGroupMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.actualMonthlyCost;
  private static final String BUDGET_GROUP_MONTHLY_BREAKDOWN_FORECAST_MONTHLY_COST =
      BudgetGroupKeys.budgetGroupMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.forecastMonthlyCost;
  private static final String BUDGET_GROUP_MONTHLY_BREAKDOWN_YEARLY_LAST_PERIOD_COST =
      BudgetGroupKeys.budgetGroupMonthlyBreakdown + "." + BudgetMonthlyBreakdownKeys.yearlyLastPeriodCost;
  private static final String LOCALE_EN = "en";

  public String save(BudgetGroup budgetGroup) {
    return hPersistence.save(budgetGroup);
  }
  public List<String> save(List<BudgetGroup> budgetGroups) {
    return hPersistence.save(budgetGroups);
  }

  public void update(String uuid, String accountId, BudgetGroup budgetGroup) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class)
                                   .field(BudgetGroupKeys.uuid)
                                   .equal(uuid)
                                   .field(BudgetGroupKeys.accountId)
                                   .equal(accountId);
    UpdateOperations<BudgetGroup> updateOperations =
        hPersistence.createUpdateOperations(BudgetGroup.class)
            .set(BudgetGroupKeys.name, budgetGroup.getName())
            .set(BudgetGroupKeys.budgetGroupAmount, budgetGroup.getBudgetGroupAmount())
            .set(BudgetGroupKeys.actualCost, budgetGroup.getActualCost())
            .set(BudgetGroupKeys.forecastCost, budgetGroup.getForecastCost())
            .set(BudgetGroupKeys.lastMonthCost, budgetGroup.getLastMonthCost())
            .set(BudgetGroupKeys.startTime, budgetGroup.getStartTime())
            .set(BudgetGroupKeys.endTime, budgetGroup.getEndTime());

    if (budgetGroup.getPeriod() != null) {
      updateOperations.set(BudgetGroupKeys.period, budgetGroup.getPeriod());
    }
    if (budgetGroup.getAlertThresholds() != null) {
      updateOperations.set(BudgetGroupKeys.alertThresholds, budgetGroup.getAlertThresholds());
    }
    if (budgetGroup.getChildEntities() != null) {
      updateOperations.set(BudgetGroupKeys.childEntities, budgetGroup.getChildEntities());
    }
    if (budgetGroup.getCascadeType() != null) {
      updateOperations.set(BudgetGroupKeys.cascadeType, budgetGroup.getCascadeType());
    }
    if (budgetGroup.getBudgetGroupMonthlyBreakdown() != null) {
      updateOperations.set(BudgetGroupKeys.budgetGroupMonthlyBreakdown, budgetGroup.getBudgetGroupMonthlyBreakdown());
    }
    if (budgetGroup.getBudgetGroupHistory() != null) {
      updateOperations.set(BudgetGroupKeys.budgetGroupHistory, budgetGroup.getBudgetGroupHistory());
    }

    hPersistence.update(query, updateOperations);
  }

  public void updateParentId(String parentId, List<String> budgetGroupIds) {
    Query<BudgetGroup> query =
        hPersistence.createQuery(BudgetGroup.class).field(BudgetGroupKeys.uuid).in(budgetGroupIds);
    UpdateOperations<BudgetGroup> updateOperations = parentId != null
        ? hPersistence.createUpdateOperations(BudgetGroup.class).set(BudgetGroupKeys.parentBudgetGroupId, parentId)
        : hPersistence.createUpdateOperations(BudgetGroup.class).unset(BudgetGroupKeys.parentBudgetGroupId);
    hPersistence.update(query, updateOperations);
  }

  public void updateChildEntities(String uuid, List<BudgetGroupChildEntityDTO> childEntities) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class).field(BudgetGroupKeys.uuid).equal(uuid);
    UpdateOperations<BudgetGroup> updateOperations =
        hPersistence.createUpdateOperations(BudgetGroup.class).set(BudgetGroupKeys.childEntities, childEntities);
    hPersistence.update(query, updateOperations);
  }

  public void unsetParent(List<String> budgetGroupIds) {
    Query<BudgetGroup> query =
        hPersistence.createQuery(BudgetGroup.class).field(BudgetGroupKeys.uuid).in(budgetGroupIds);
    UpdateOperations<BudgetGroup> updateOperations =
        hPersistence.createUpdateOperations(BudgetGroup.class).unset(BudgetGroupKeys.parentBudgetGroupId);
    hPersistence.update(query, updateOperations);
  }

  public void updateBreakdownCosts(
      String uuid, Double[] actualCosts, Double[] forecastCosts, Double[] lastPeriodCosts) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class).field(BudgetGroupKeys.uuid).equal(uuid);
    UpdateOperations<BudgetGroup> updateOperations =
        hPersistence.createUpdateOperations(BudgetGroup.class)
            .set(BUDGET_GROUP_MONTHLY_BREAKDOWN_ACTUAL_MONTHLY_COST, actualCosts)
            .set(BUDGET_GROUP_MONTHLY_BREAKDOWN_FORECAST_MONTHLY_COST, forecastCosts)
            .set(BUDGET_GROUP_MONTHLY_BREAKDOWN_YEARLY_LAST_PERIOD_COST, lastPeriodCosts);
    hPersistence.update(query, updateOperations);
  }

  public void updateBudgetGroupAmount(String uuid, Double budgetGroupAmount) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class).field(BudgetGroupKeys.uuid).equal(uuid);
    UpdateOperations<BudgetGroup> updateOperations = hPersistence.createUpdateOperations(BudgetGroup.class)
                                                         .set(BudgetGroupKeys.budgetGroupAmount, budgetGroupAmount);
    hPersistence.update(query, updateOperations);
  }

  public void updateBudgetGroupAmountInBreakdown(String uuid, List<ValueDataPoint> monthlyBudgetGroupAmounts) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class).field(BudgetGroupKeys.uuid).equal(uuid);
    UpdateOperations<BudgetGroup> updateOperations =
        hPersistence.createUpdateOperations(BudgetGroup.class)
            .set(BUDGET_GROUP_MONTHLY_BREAKDOWN_MONTHLY_AMOUNTS, monthlyBudgetGroupAmounts);
    hPersistence.update(query, updateOperations);
  }

  public BudgetGroup get(String uuid, String accountId) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class)
                                   .field(BudgetGroupKeys.uuid)
                                   .equal(uuid)
                                   .field(BudgetGroupKeys.accountId)
                                   .equal(accountId);
    return query.get();
  }

  public List<BudgetGroup> list(String accountId) {
    return list(accountId, Integer.MAX_VALUE - 1, 0, null, null);
  }

  public List<BudgetGroup> list(String accountId, Integer count, Integer startIndex,
      BudgetGroupSortType budgetGroupSortType, CCMSortOrder ccmSortOrder) {
    Query<BudgetGroup> query =
        hPersistence.createQuery(BudgetGroup.class).field(BudgetGroupKeys.accountId).equal(accountId);

    final BudgetGroupSortType finalBudgetGroupSortType =
        Objects.isNull(budgetGroupSortType) ? BudgetGroupSortType.BUDGET_GROUP_NAME : budgetGroupSortType;
    final Sort sort = (Objects.isNull(ccmSortOrder) || ccmSortOrder == CCMSortOrder.ASCENDING)
        ? Sort.ascending(finalBudgetGroupSortType.getColumnName())
        : Sort.descending(finalBudgetGroupSortType.getColumnName());

    final FindOptions options = new FindOptions();
    if (budgetGroupSortType.equals(BudgetGroupSortType.BUDGET_GROUP_NAME)) {
      options.collation(Collation.builder().locale(LOCALE_EN).build());
    }
    options.limit(count);
    options.skip(startIndex);

    return query.order(sort).asList(options);
  }

  public List<BudgetGroup> list(String accountId, List<BudgetPeriod> budgetPeriods, Integer count, Integer startIndex) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class)
                                   .field(BudgetGroupKeys.accountId)
                                   .equal(accountId)
                                   .field(BudgetGroupKeys.period)
                                   .in(budgetPeriods);
    return query.asList(new FindOptions().skip(startIndex).limit(count));
  }

  public List<BudgetGroup> list(String accountId, String budgetGroupName) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class)
                                   .field(BudgetGroupKeys.accountId)
                                   .equal(accountId)
                                   .field(BudgetGroupKeys.name)
                                   .equal(budgetGroupName);
    return query.asList();
  }

  public List<BudgetGroup> list(String accountId, List<String> budgetGroupIds) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class)
                                   .field(BudgetGroupKeys.accountId)
                                   .equal(accountId)
                                   .field(BudgetGroupKeys.uuid)
                                   .in(budgetGroupIds);
    return query.asList();
  }

  public boolean delete(String uuid, String accountId) {
    Query<BudgetGroup> query = hPersistence.createQuery(BudgetGroup.class)
                                   .field(BudgetGroupKeys.accountId)
                                   .equal(accountId)
                                   .field(BudgetGroupKeys.uuid)
                                   .equal(uuid);
    return hPersistence.delete(query);
  }
}
