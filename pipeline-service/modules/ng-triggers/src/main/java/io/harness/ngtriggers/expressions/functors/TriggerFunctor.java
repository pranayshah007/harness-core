/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions.functors;

import static io.harness.ngtriggers.Constants.CONNECTOR_REF;
import static io.harness.ngtriggers.Constants.EVENT_PAYLOAD;
import static io.harness.ngtriggers.Constants.HEADER;
import static io.harness.ngtriggers.Constants.PAYLOAD;
import static io.harness.ngtriggers.Constants.TRIGGER_PAYLOAD;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.LateBindingValue;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerFunctor implements LateBindingValue {
  private final Ambiance ambiance;
  private final PlanExecutionMetadataService planExecutionMetadataService;

  public TriggerFunctor(Ambiance ambiance, PlanExecutionMetadataService planExecutionMetadataService) {
    this.ambiance = ambiance;
    this.planExecutionMetadataService = planExecutionMetadataService;
  }

  @Override
  public Object bind() {
    PlanExecutionMetadata metadata =
        planExecutionMetadataService.findByPlanExecutionId(ambiance.getPlanExecutionId())
            .orElseThrow(()
                             -> new IllegalStateException(
                                 "No Metadata present for planExecution :" + ambiance.getPlanExecutionId()));
    Map<String, Object> jsonObject = TriggerHelper.buildJsonObjectFromAmbiance(metadata.getTriggerPayload());
    if (null != metadata.getTriggerPayload()
        && EmptyPredicate.isNotEmpty(metadata.getTriggerPayload().getConnectorRef())) {
      jsonObject.put(CONNECTOR_REF, metadata.getTriggerPayload().getConnectorRef());
      jsonObject.put(TRIGGER_PAYLOAD, metadata.getTriggerPayload());
    }

    if (EmptyPredicate.isNotEmpty(metadata.getTriggerHeader())) {
      jsonObject.put(HEADER, new TriggerHeaderBindingMap(metadata.getTriggerHeader()));
    }

    if (isNotBlank(metadata.getTriggerJsonPayload())) {
      jsonObject.put(EVENT_PAYLOAD, metadata.getTriggerJsonPayload());
      // payload
      try {
        jsonObject.put(PAYLOAD, JsonPipelineUtils.read(metadata.getTriggerJsonPayload(), HashMap.class));
      } catch (IOException toHashMapEx) {
        try {
          jsonObject.put(PAYLOAD, JsonPipelineUtils.read(metadata.getTriggerJsonPayload(), List.class));
        } catch (IOException toListEx) {
          throw new InvalidRequestException("Event payload could not be converted to a hashmap or list");
        }
      }
    }
    return jsonObject;
  }
}
