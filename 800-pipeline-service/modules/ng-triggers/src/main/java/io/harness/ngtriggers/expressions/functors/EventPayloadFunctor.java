/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.io.IOException;
import java.util.HashMap;

@OwnedBy(PIPELINE)
public class EventPayloadFunctor implements LateBindingValue {
  private final Ambiance ambiance;
  private final PlanExecutionMetadataService planExecutionMetadataService;

  public EventPayloadFunctor(Ambiance ambiance, PlanExecutionMetadataService planExecutionMetadataService) {
    this.ambiance = ambiance;
    this.planExecutionMetadataService = planExecutionMetadataService;
  }

  @Override
  public Object bind() {
    try {
      PlanExecutionMetadata planExecutionMetadata =
          planExecutionMetadataService.findByPlanExecutionId(ambiance.getPlanExecutionId())
              .orElseThrow(()
                               -> new IllegalStateException(
                                   "PlanExecution metadata null for planExecutionId " + ambiance.getPlanExecutionId()));
      return JsonPipelineUtils.read(planExecutionMetadata.getTriggerJsonPayload(), HashMap.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Event payload could not be converted to a hashmap");
    }
  }
}
