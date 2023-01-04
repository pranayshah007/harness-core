/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.dao;

import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.BudgetGroup.BudgetGroupKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

public class BudgetGroupDao {
  @Inject private HPersistence hPersistence;

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
            .set(BudgetGroupKeys.parentBudgetGroupId, budgetGroup.getParentBudgetGroupId())
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

  public List<BudgetGroup> list(String accountId, Integer count, Integer startIndex) {
    Query<BudgetGroup> query =
        hPersistence.createQuery(BudgetGroup.class).field(BudgetGroupKeys.accountId).equal(accountId);
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
