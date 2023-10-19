/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.evaluators;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expression.EngineExpressionEvaluatorResolver;
import io.harness.pms.expression.ParameterFieldResolverFunctor;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@Getter
public class ProvisionerExpressionEvaluator extends EngineExpressionEvaluator {
  private final Map<String, Object> output;
  private InputSetValidatorFactory inputSetValidatorFactory;

  public ProvisionerExpressionEvaluator(Map<String, Object> output, InputSetValidatorFactory inputSetValidatorFactory) {
    super(null);
    this.output = output;
    this.inputSetValidatorFactory = inputSetValidatorFactory;
  }

  @Override
  protected void initialize() {
    super.initialize();
    this.addToContext("provisioner", ProvisionerFunctor.builder().output(output).build());
  }

  /**
   * Evaluate the properties value and return the map with the names and evaluated values.
   * If the property value is unresolved the expressions will be put in the map.
   *
   * @param properties the map of properties where property value is expression
   * @param contextMap context
   * @return evaluated map of properties
   */

  public Map<String, Object> evaluateProperties(Map<String, String> properties, Map<String, Object> contextMap) {
    Map<String, Object> propertyNameEvaluatedMap = new HashMap<>();
    for (Map.Entry<String, String> property : properties.entrySet()) {
      if (isEmpty(property.getValue())) {
        continue;
      }
      if (!EngineExpressionEvaluator.hasExpressions(property.getValue())) {
        propertyNameEvaluatedMap.put(property.getKey(), property.getValue());
        continue;
      }
      Object evaluated =
          renderExpression(property.getValue(), contextMap, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

      propertyNameEvaluatedMap.put(property.getKey(), evaluated);
    }
    return propertyNameEvaluatedMap;
  }

  public <T> T evaluateExpression(ParameterField<T> parameterField, ExpressionMode expressionMode) {
    if (parameterField == null || parameterField.getValue() == null) {
      return null;
    }

    Object finalValue = parameterField.fetchFinalValue();
    if (finalValue instanceof String) {
      return (T) evaluateExpression((String) finalValue, expressionMode);
    }

    return parameterField.getValue();
  }

  @Override
  public Object resolve(Object o, ExpressionMode expressionMode) {
    return ExpressionEvaluatorUtils.updateExpressions(o,
        new ParameterFieldResolverFunctor(
            new EngineExpressionEvaluatorResolver(this), inputSetValidatorFactory, expressionMode));
  }
}
