package io.harness.pms.expression;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class NoopEngineExpressionServiceImpl implements EngineExpressionService {
  @Override
  public String renderExpression(Ambiance ambiance, String expression, boolean skipUnresolvedExpressionsCheck) {
    return null;
  }

  @Override
  public Object evaluateExpression(Ambiance ambiance, String expression) {
    return null;
  }
}