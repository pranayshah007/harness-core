/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping.BusinessMappingKeys;

@OwnedBy(CE)
public enum CostCategorySortType {
  NAME(BusinessMappingKeys.name),
  LAST_EDIT(BusinessMappingKeys.lastUpdatedAt);

  private final String columnName;

  CostCategorySortType(final String columnName) {
    this.columnName = columnName;
  }

  public String getColumnName() {
    return columnName;
  }
}
