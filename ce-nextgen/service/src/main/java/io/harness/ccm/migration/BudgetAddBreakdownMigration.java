/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.migration;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetMonthlyBreakdown;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class BudgetAddBreakdownMigration implements NGMigration {
  @Inject private BudgetDao budgetDao;
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    try {
      log.info("Starting migration (updates) of all Budgets, adding object breakdown as YEARLY to all budgets");
      final List<Budget> budgetList = hPersistence.createQuery(Budget.class, excludeAuthority).asList();
      for (final Budget budget : budgetList) {
        // For every budget we are adding default breakdown as YEARLY
        try {
          migrateBudgetBreakdownObject(budget.getUuid());
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, budgetId {}", budget.getAccountId(), budget.getUuid(), e);
        }
      }
      log.info("BudgetAddBreakdownMigration has been completed");
    } catch (final Exception e) {
      log.error("Failure occurred in BudgetAddBreakdownMigration", e);
    }
  }

  private void migrateBudgetBreakdownObject(final String budgetUuid) {
    budgetDao.updateBudgetMonthlyBreakdown(
        budgetUuid, BudgetMonthlyBreakdown.builder().budgetBreakdown(BudgetBreakdown.YEARLY).build());
  }
}
