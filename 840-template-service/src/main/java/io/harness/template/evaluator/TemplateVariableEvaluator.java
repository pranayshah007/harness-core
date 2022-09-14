package io.harness.template.evaluator;

import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.EngineExpressionEvaluator;

import java.util.Map;

public class TemplateVariableEvaluator extends EngineExpressionEvaluator {
    private final Map<String, String> templateVariableValues;
    public TemplateVariableEvaluator(Map<String, String> templateVariableValues) {
        super(null);
        this.templateVariableValues = templateVariableValues;
    }

    @Override
    protected void initialize() {
        super.initialize();
        if(EmptyPredicate.isNotEmpty(templateVariableValues)) {
            templateVariableValues.forEach(this::addToContext);
        }
    }
}
