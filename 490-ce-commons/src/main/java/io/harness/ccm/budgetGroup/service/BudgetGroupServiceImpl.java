/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.service;

import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.budgetGroup.utils.BudgetGroupUtils;

import com.google.inject.Inject;
import java.util.List;

public class BudgetGroupServiceImpl implements BudgetGroupService {
  @Inject BudgetGroupDao budgetGroupDao;

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
}
