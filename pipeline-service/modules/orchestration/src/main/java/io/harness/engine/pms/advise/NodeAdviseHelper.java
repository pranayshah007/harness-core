/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.pms.advise.publisher.NodeAdviseEventPublisher;
import io.harness.execution.NodeExecution;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.SdkResponseEventUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.execution.events.node.advise.NodeAdviseBaseHandler;
import io.harness.pms.sdk.core.registries.AdviserRegistry;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class NodeAdviseHelper implements NodeAdviseBaseHandler {
  @Inject private NodeAdviseEventPublisher nodeAdviseEventPublisher;
  @Inject private AdviserRegistry adviserRegistry;

  public void queueAdvisingEvent(NodeExecution nodeExecution, Node planNode, Status fromStatus) {
    nodeAdviseEventPublisher.publishEvent(nodeExecution, planNode, fromStatus);
  }

  public void queueAdvisingEvent(
      NodeExecution nodeExecution, FailureInfo failureInfo, Node planNode, Status fromStatus) {
    nodeAdviseEventPublisher.publishEvent(nodeExecution, failureInfo, planNode, fromStatus);
  }

  public SdkResponseEventProto getResponseInCaseOfNoCustomAdviser(
      NodeExecution nodeExecution, Node planNode, Status fromStatus) {
    AdviseEvent event =
        NodeAdviserUtils.createAdviseEvent(nodeExecution, nodeExecution.getFailureInfo(), planNode, fromStatus);
    try {
      AdviserResponse adviserResponse = handleAdviseEvent(event);
      SdkResponseEventProto handleAdviserResponseRequest = null;
      if (adviserResponse == null) {
        adviserResponse = AdviserResponse.newBuilder().setType(AdviseType.UNKNOWN).build();
      }
      log.debug("Calculated Adviser response is of type {}", adviserResponse.getType());
      handleAdviserResponseRequest =
          SdkResponseEventUtils.getSdkResponse(event.getAmbiance(), event.getNotifyId(), adviserResponse);
      return handleAdviserResponseRequest;
    } catch (Exception ex) {
      log.error("Error while advising execution", ex);
      if (isEmpty(event.getNotifyId())) {
        log.debug("NotifyId is empty for nodeExecutionId {} and planExecutionId {}. Nothing will happen.",
            AmbianceUtils.obtainCurrentRuntimeId(event.getAmbiance()), event.getAmbiance().getPlanExecutionId());
        return null;
      } else {
        return SdkResponseEventUtils.getSdkErrorResponse(NodeExecutionEventType.ADVISE, event.getAmbiance(),
            event.getNotifyId(), NodeExecutionUtils.constructFailureInfo(ex));
      }
    }
  }

  @Override
  public AdviserRegistry getAdviserRegistry() {
    return adviserRegistry;
  }
}
