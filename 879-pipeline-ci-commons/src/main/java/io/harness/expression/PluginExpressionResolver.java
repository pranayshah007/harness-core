/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PluginExpressionResolver {
  @Inject private EngineExpressionService engineExpressionService;

  public Object updateExpressions(Ambiance ambiance, Object obj) {
    if (obj == null) {
      return obj;
    }
    return ExpressionEvaluatorUtils.updateExpressions(
        obj, new PluginExpressionResolveFunctor(engineExpressionService, ambiance));
  }
}
