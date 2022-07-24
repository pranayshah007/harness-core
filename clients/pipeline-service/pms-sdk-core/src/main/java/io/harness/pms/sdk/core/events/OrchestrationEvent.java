/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.events;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "OrchestrationEventKeys")
public class OrchestrationEvent {
  @NotNull Ambiance ambiance;
  String serviceName;
  Status status;
  StepParameters resolvedStepParameters;

  @NotNull OrchestrationEventType eventType;
  TriggerPayload triggerPayload;
  List<String> tags;
  PipelineModuleInfo moduleInfo;
  Long endTs;

  public AutoLogContext autoLogContext() {
    Map<String, String> logContext = AmbianceUtils.logContextMap(ambiance);
    logContext.put(OrchestrationEventKeys.eventType, eventType.name());
    return new AutoLogContext(logContext, OVERRIDE_NESTS);
  }
}
