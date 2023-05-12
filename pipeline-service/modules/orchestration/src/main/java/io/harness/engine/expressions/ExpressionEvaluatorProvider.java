/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import io.harness.engine.expressions.functors.NodeExecutionEntityType;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.VariableResolverTracker;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.Map;
import java.util.Set;

public interface ExpressionEvaluatorProvider {
  /**
   * Provides an instance of {@link EngineExpressionEvaluator}. Must never return {@code null}.
   *
   * @param variableResolverTracker used by EngineExpressionEvaluator constructor
   * @param ambiance                used by AmbianceExpressionEvaluator constructor
   * @param entityTypes             used by AmbianceExpressionEvaluator constructor
   * @param refObjectSpecific       used by AmbianceExpressionEvaluator constructor
   * @return a new instance of EngineExpressionEvaluator
   */
  default EngineExpressionEvaluator get(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific) {
    return get(variableResolverTracker, ambiance, entityTypes, refObjectSpecific, null);
  }
  EngineExpressionEvaluator get(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, String> contextMap);
}
