/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events.BudgetGroup;

import io.harness.ccm.budgetGroup.BudgetGroup;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BudgetGroupCreateEvent extends BudgetGroupEvent {
  public static final String BUDGET_GROUP_CREATED = "BudgetGroupCreated";

  public BudgetGroupCreateEvent(String accountIdentifier, BudgetGroup budgetGroupDTO) {
    super(accountIdentifier, budgetGroupDTO);
  }

  @Override
  public String getEventType() {
    return BUDGET_GROUP_CREATED;
  }
}
