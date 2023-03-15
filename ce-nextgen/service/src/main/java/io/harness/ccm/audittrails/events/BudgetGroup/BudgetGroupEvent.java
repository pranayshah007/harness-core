/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events.BudgetGroup;

import static io.harness.audit.ResourceTypeConstants.BUDGET_GROUP;

import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public abstract class BudgetGroupEvent implements Event {
  private BudgetGroup budgetGroupDTO;
  private String accountIdentifier;

  public BudgetGroupEvent(String accountIdentifier, BudgetGroup budgetGroupDTO) {
    this.accountIdentifier = accountIdentifier;
    this.budgetGroupDTO = budgetGroupDTO;
  }

  @Override
  @JsonIgnore
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @Override
  @JsonIgnore
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, budgetGroupDTO.getName());
    return Resource.builder().identifier(budgetGroupDTO.getUuid()).type(BUDGET_GROUP).labels(labels).build();
  }
}
