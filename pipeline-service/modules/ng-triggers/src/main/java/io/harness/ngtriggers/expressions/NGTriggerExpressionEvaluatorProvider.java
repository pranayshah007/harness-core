/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.expressions.NGTriggerExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;

public class NGTriggerExpressionEvaluatorProvider {
  @Inject PlanExecutionMetadataService planExecutionMetadataService;

  public NGTriggerExpressionEvaluator get(Ambiance ambiance) {
    return new NGTriggerExpressionEvaluator(ambiance, planExecutionMetadataService);
  }
}
