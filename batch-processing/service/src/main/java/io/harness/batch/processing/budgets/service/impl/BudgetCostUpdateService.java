/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.budgets.service.impl;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.graphql.core.budget.BudgetService;

import software.wings.graphql.datafetcher.billing.CloudBillingHelper;

import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class BudgetCostUpdateService {
  @Autowired private AccountShardService accountShardService;
  @Autowired private CloudBillingHelper cloudBillingHelper;
  @Autowired private BatchMainConfig mainConfiguration;
  @Autowired private BudgetDao budgetDao;
  @Autowired private BudgetService budgetService;

  public void updateCosts() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    log.info("ceEnabledAccounts ids list {}", accountIds);

    accountIds.forEach(accountId -> {
      List<Budget> budgets = budgetDao.list(accountId);
      budgets.forEach(budget -> {
        updateBudgetAmount(budget);
        budgetService.updateBudgetCosts(budget);
        budgetDao.update(budget.getUuid(), budget);
      });
    });
  }

  public void updateBudgetAmount(Budget budget) {
    try {
      if (BudgetUtils.getStartOfCurrentDay() >= budget.getEndTime() && budget.getStartTime() != 0) {
        budget.setStartTime(budget.getEndTime());
        budget.setEndTime(BudgetUtils.getEndTimeForBudget(budget.getStartTime(), budget.getPeriod()));
        budget.setBudgetAmount(BudgetUtils.getUpdatedBudgetAmount(budget));
        budgetDao.update(budget.getUuid(), budget);
        // Todo: Insert update entry in budget history table
      }
    } catch (Exception e) {
      log.info("Failed to update budget amount for budget {}", budget.getUuid());
    }
  }
}
