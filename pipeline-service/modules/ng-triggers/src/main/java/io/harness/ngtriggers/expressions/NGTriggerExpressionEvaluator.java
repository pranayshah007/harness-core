/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.expressions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.ngtriggers.expressions.functors.EventPayloadFunctor;
import io.harness.ngtriggers.expressions.functors.TriggerFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

@OwnedBy(HarnessTeam.SPG)
public class NGTriggerExpressionEvaluator extends EngineExpressionEvaluator {
  private PlanExecutionMetadataService planExecutionMetadataService;

  protected final Ambiance ambiance;
  public NGTriggerExpressionEvaluator(Ambiance ambiance, PlanExecutionMetadataService planExecutionMetadataService) {
    super(null);
    this.ambiance = ambiance;
    this.planExecutionMetadataService = planExecutionMetadataService;
  }
  @Override
  protected void initialize() {
    super.initialize();
    addToContext(SetupAbstractionKeys.trigger, new TriggerFunctor(ambiance, planExecutionMetadataService));
    addToContext(SetupAbstractionKeys.eventPayload, new EventPayloadFunctor(ambiance, planExecutionMetadataService));
  }
}
