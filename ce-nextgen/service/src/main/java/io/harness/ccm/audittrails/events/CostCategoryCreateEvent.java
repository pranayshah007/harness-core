/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.businessmapping.entities.BusinessMapping;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CostCategoryCreateEvent extends CostCategoryEvent {
  public static final String COST_CATEGORY_CREATED = "CostCategoryCreated";

  public CostCategoryCreateEvent(String accountIdentifier, BusinessMapping costCategoryDTO) {
    super(accountIdentifier, costCategoryDTO);
  }

  @Override
  public String getEventType() {
    return COST_CATEGORY_CREATED;
  }
}
