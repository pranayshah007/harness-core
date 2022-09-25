/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.expressionEvaluator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionMode;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The purpose of CDEngineExpressionEvaluator is to dynamically evaluate expressions based on the context that is
 * supplied to methods. This evaluator cannot evaluate PMS expressions because it doesn't make calls to PMS service. If
 * you need to evaluate PMS expressions use {@link io.harness.cdng.expressions.CDExpressionResolver}
 */
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CDEngineExpressionEvaluator extends EngineExpressionEvaluator {
  public CDEngineExpressionEvaluator() {
    super(null);
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
}
