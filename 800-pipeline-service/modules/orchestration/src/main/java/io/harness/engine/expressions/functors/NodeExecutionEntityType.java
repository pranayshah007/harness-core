/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.EnumSet;
import java.util.Set;

@OwnedBy(CDC)
public enum NodeExecutionEntityType {
  NODE_EXECUTION_FIELDS,
  STEP_PARAMETERS,
  OUTCOME,
  SWEEPING_OUTPUT;

  public static Set<NodeExecutionEntityType> allEntities() {
    return EnumSet.of(NODE_EXECUTION_FIELDS, STEP_PARAMETERS, OUTCOME, SWEEPING_OUTPUT);
  }
}
