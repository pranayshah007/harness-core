/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events;

import io.harness.ccm.commons.entities.billing.Budget;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BudgetUpdateEvent extends BudgetEvent {
  public static final String BUDGET_UPDATED = "BudgetUpdated";
  private Budget oldBudgetDTO;

  public BudgetUpdateEvent(String accountIdentifier, Budget newBudgetDTO, Budget oldBudgetDTO) {
    super(accountIdentifier, newBudgetDTO);
    this.oldBudgetDTO = oldBudgetDTO;
  }

  @Override
  public String getEventType() {
    return BUDGET_UPDATED;
  }
}
