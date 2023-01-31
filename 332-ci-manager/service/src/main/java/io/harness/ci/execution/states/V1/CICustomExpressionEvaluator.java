package io.harness.ci.states.V1;

import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.VariableResolverTracker;

import java.util.Map;

public class CICustomExpressionEvaluator extends EngineExpressionEvaluator {
    public CICustomExpressionEvaluator(VariableResolverTracker variableResolverTracker) {
        super(variableResolverTracker);
    }

    public CICustomExpressionEvaluator(VariableResolverTracker variableResolverTracker, Map<String, Object> contextMap) {
        super(variableResolverTracker);
        this.addToContext("stage",contextMap);
    }
}