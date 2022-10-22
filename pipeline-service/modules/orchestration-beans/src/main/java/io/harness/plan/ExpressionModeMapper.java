package io.harness.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.ExpressionMode;

import lombok.experimental.UtilityClass;

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
}
