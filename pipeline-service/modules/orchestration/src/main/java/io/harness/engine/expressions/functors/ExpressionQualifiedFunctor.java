package io.harness.engine.expressions.functors;

import io.harness.expression.functors.ExpressionFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.planExecutionJson.PlanExpansionService;

public class ExpressionQualifiedFunctor implements ExpressionFunctor {
  PlanExpansionService planExpansionService;
  Ambiance ambiance;

  public ExpressionQualifiedFunctor(Ambiance ambiance, PlanExpansionService planExpansionService) {
    this.planExpansionService = planExpansionService;
    this.ambiance = ambiance;
  }

  public Object getJson(String fqn) {
    return planExpansionService.resolveExpression(ambiance.getPlanExecutionId(), fqn);
  }
}
