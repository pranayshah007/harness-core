/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.start;

import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionTimeoutCallback;
import io.harness.engine.executions.node.NodeExecutionUpdateFailedException;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.observer.Subject;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.start.NodeStartEvent;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.springdata.TransactionHelper;
import io.harness.timeout.TimeoutCallback;
import io.harness.timeout.TimeoutEngine;
import io.harness.timeout.TimeoutInstance;
import io.harness.timeout.TimeoutParameters;
import io.harness.timeout.contracts.TimeoutObtainment;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class NodeStartHelper {
  @Inject private PmsEventSender eventSender;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private TimeoutEngine timeoutEngine;
  @Inject private PlanService planService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private TransactionHelper transactionHelper;
  @Inject private PmsGraphStepDetailsService pmsGraphStepDetailsService;
  @Getter private final Subject<NodeExecutionStartObserver> nodeExecutionStartSubject = new Subject<>();

  public void startNode(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    Status targetStatus = calculateStatusFromMode(facilitatorResponse.getExecutionMode());
    PlanNode node = planService.fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    NodeExecution nodeExecution = prepareNodeExecutionForInvocation(ambiance, targetStatus, node);
    if (nodeExecution == null) {
      nodeExecution = nodeExecutionService.get(nodeExecutionId);
      // We can mark the nodeExecution as either discontinuing, aborted or expired if nodeExecution is in queued state.
      // If the nodeExecution is in that state then we should do no-op
      if (StatusUtils.abortInProgressStatuses().contains(nodeExecution.getStatus())) {
        return;
      }
      // This is just for debugging if this is happening then the node status has changed from QUEUED
      // This should never happen
      log.warn("Not Starting node execution. Cannot transition from {} to {}", nodeExecution.getStatus(), targetStatus);
      throw new NodeExecutionUpdateFailedException("Cannot Start node Execution");
    }
    nodeExecutionStartSubject.fireInform(
        NodeExecutionStartObserver::onNodeStart, NodeStartInfo.builder().nodeExecution(nodeExecution).build());
    log.info("Sending NodeExecution START event");
    sendEvent(nodeExecution, node, facilitatorResponse.getPassThroughDataBytes());
  }

  private void sendEvent(NodeExecution nodeExecution, PlanNode planNode, ByteString passThroughData) {
    NodeStartEvent nodeStartEvent = NodeStartEvent.newBuilder()
                                        .setAmbiance(nodeExecution.getAmbiance())
                                        .addAllRefObjects(planNode.getRefObjects())
                                        .setFacilitatorPassThoroughData(passThroughData)
                                        .setStepParameters(nodeExecution.getResolvedStepParametersBytes())
                                        .setMode(nodeExecution.getMode())
                                        .build();
    eventSender.sendEvent(nodeExecution.getAmbiance(), nodeStartEvent.toByteString(), PmsEventCategory.NODE_START,
        nodeExecution.getModule(), true);
  }

  private NodeExecution prepareNodeExecutionForInvocation(Ambiance ambiance, Status targetStatus, PlanNode planNode) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    return transactionHelper.performTransaction(() -> {
      List<String> timeoutInstanceIds = registerTimeouts(ambiance, planNode.getTimeoutObtainments());
      resolveInputs(ambiance, planNode);
      return nodeExecutionService.updateStatusWithOps(nodeExecutionId, targetStatus, ops -> {
        setUnset(ops, NodeExecutionKeys.timeoutInstanceIds, timeoutInstanceIds);
        updateStartTsInNodeExecution(ops, ambiance);
      }, EnumSet.noneOf(Status.class));
    });
  }

  private Status calculateStatusFromMode(ExecutionMode executionMode) {
    switch (executionMode) {
      case CONSTRAINT:
        return Status.RESOURCE_WAITING;
      case APPROVAL:
        return Status.APPROVAL_WAITING;
      case WAIT_STEP:
        return Status.WAIT_STEP_RUNNING;
      case ASYNC:
        return Status.ASYNC_WAITING;
      default:
        return Status.RUNNING;
    }
  }

  private List<String> registerTimeouts(Ambiance ambiance, List<TimeoutObtainment> timeoutObtainments) {
    if (EmptyPredicate.isEmpty(timeoutObtainments)) {
      return Collections.emptyList();
    }
    List<String> timeoutInstanceIds = new ArrayList<>();
    TimeoutCallback timeoutCallback =
        new NodeExecutionTimeoutCallback(ambiance.getPlanExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    EngineExpressionEvaluator evaluator = pmsEngineExpressionService.prepareExpressionEvaluator(ambiance);
    for (TimeoutObtainment timeoutObtainment : timeoutObtainments) {
      TimeoutParameters timeoutParameters =
          OrchestrationUtils.buildTimeoutParameters(kryoSerializer, evaluator, timeoutObtainment);
      TimeoutInstance instance =
          timeoutEngine.registerTimeout(timeoutObtainment.getDimension(), timeoutParameters, timeoutCallback);
      timeoutInstanceIds.add(instance.getUuid());
    }
    log.info(format("Registered node execution timeouts: %s", timeoutInstanceIds.toString()));
    return timeoutInstanceIds;
  }

  // TODO(archit): Enable all FF for engine here as well, or globally enable them
  private void resolveInputs(Ambiance ambiance, PlanNode planNode) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    if (planNode.getStepInputs() != null) {
      log.info("Starting to Resolve step Inputs");
      List<String> enabledFeatureFlags = new LinkedList<>();
      if (AmbianceUtils.shouldUseExpressionEngineV2(ambiance)) {
        enabledFeatureFlags.add(EngineExpressionEvaluator.PIE_EXECUTION_JSON_SUPPORT);
      }
      Object resolvedInputs = pmsEngineExpressionService.resolve(
          ambiance, planNode.getStepInputs(), planNode.getExpressionMode(), enabledFeatureFlags);
      PmsStepParameters parameterInputs =
          PmsStepParameters.parse(OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(resolvedInputs));
      pmsGraphStepDetailsService.saveNodeExecutionInfo(nodeExecutionId, ambiance.getPlanExecutionId(), parameterInputs);
      log.info("Resolved step Inputs");
    } else {
      pmsGraphStepDetailsService.saveNodeExecutionInfo(nodeExecutionId, ambiance.getPlanExecutionId(), null);
    }
  }

  @VisibleForTesting
  protected void updateStartTsInNodeExecution(Update ops, Ambiance ambiance) {
    long currentTimeMillis = System.currentTimeMillis();
    Level updatedLevel =
        ambiance.toBuilder().getLevelsBuilder(ambiance.getLevelsCount() - 1).setStartTs(currentTimeMillis).build();
    ambiance = ambiance.toBuilder().setLevels(ambiance.getLevelsCount() - 1, updatedLevel).build();

    ops.set(NodeExecutionKeys.startTs, currentTimeMillis);
    ops.set(NodeExecutionKeys.ambiance, ambiance);
  }
}
