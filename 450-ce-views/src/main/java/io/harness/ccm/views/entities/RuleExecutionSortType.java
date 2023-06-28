/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.entities.RuleExecution.RuleExecutionKeys;

@OwnedBy(CE)
public enum RuleExecutionSortType {
  COST(RuleExecutionKeys.cost),
  LAST_UPDATED_AT(RuleExecutionKeys.lastUpdatedAt);

  private final String columnName;

  RuleExecutionSortType(final String columnName) {
    this.columnName = columnName;
  }

  public String getColumnName() {
    return columnName;
  }
}
