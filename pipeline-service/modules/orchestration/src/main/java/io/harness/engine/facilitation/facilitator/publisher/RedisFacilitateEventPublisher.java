/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation.facilitator.publisher;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.facilitators.FacilitatorEvent;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RedisFacilitateEventPublisher implements FacilitateEventPublisher {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsEventSender eventSender;

  @Override
  public String publishEvent(Ambiance ambiance, PlanNode node) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.forFacilitation);
    FacilitatorEvent event = FacilitatorEvent.newBuilder()
                                 .setNodeExecutionId(nodeExecutionId)
                                 .setAmbiance(ambiance)
                                 .setStepParameters(nodeExecution.getResolvedStepParametersBytes())
                                 .setStepType(node.getStepType())
                                 .setNotifyId(generateUuid())
                                 .addAllRefObjects(node.getRefObjects())
                                 .addAllFacilitatorObtainments(node.getFacilitatorObtainments())
                                 .build();

    return eventSender.sendEvent(
        ambiance, event.toByteString(), PmsEventCategory.FACILITATOR_EVENT, nodeExecution.getModule(), true);
  }
}
