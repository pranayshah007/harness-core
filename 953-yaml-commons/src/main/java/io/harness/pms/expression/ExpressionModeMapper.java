/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expression;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.common.ExpressionMode;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class ExpressionModeMapper {
  public static ExpressionMode fromExpressionModeProto(io.harness.pms.contracts.plan.ExpressionMode mode) {
    if (mode == null || mode == io.harness.pms.contracts.plan.ExpressionMode.UNRECOGNIZED) {
      return ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
    }
    switch (mode.getNumber()) {
      case 2:
        return ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
      case 3:
        return ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED;
      default:
        return ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
    }
  }

  public static io.harness.pms.contracts.plan.ExpressionMode toExpressionModeProto(ExpressionMode mode) {
    if (mode == null || mode == ExpressionMode.UNKNOWN_MODE) {
      return io.harness.pms.contracts.plan.ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
    }
    switch (mode) {
      case THROW_EXCEPTION_IF_UNRESOLVED:
        return io.harness.pms.contracts.plan.ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED;
      case RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED:
        return io.harness.pms.contracts.plan.ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
      default:
        return io.harness.pms.contracts.plan.ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
    }
  }
}
