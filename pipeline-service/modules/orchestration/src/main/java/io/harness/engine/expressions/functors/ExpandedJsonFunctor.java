/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;

import lombok.Builder;

@OwnedBy(HarnessTeam.PIPELINE)
@Builder
public class ExpandedJsonFunctor implements LateBindingValue {
  Ambiance ambiance;
  PlanExpansionService planExpansionService;

  @Override
  public Object bind() {
    return planExpansionService.resolveExpression(ambiance, "pipeline");
  }
}
