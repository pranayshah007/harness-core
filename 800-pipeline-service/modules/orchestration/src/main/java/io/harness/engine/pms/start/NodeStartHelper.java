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
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.start.NodeStartEvent;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.PmsEngineExpressionService;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.timeout.SdkTimeoutTrackerParameters;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.springdata.TransactionHelper;
import io.harness.timeout.TimeoutCallback;
import io.harness.timeout.TimeoutEngine;
import io.harness.timeout.TimeoutInstance;
import io.harness.timeout.TimeoutParameters;
import io.harness.timeout.contracts.TimeoutObtainment;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class NodeStartHelper {
  @Inject private PmsEventSender eventSender;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private TimeoutEngine timeoutEngine;
  @Inject private PlanService planService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private TransactionHelper transactionHelper;
  @Inject private PmsGraphStepDetailsService pmsGraphStepDetailsService;

  public void startNode(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    Status targetStatus = calculateStatusFromMode(facilitatorResponse.getExecutionMode());
    PlanNode node = planService.fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    NodeExecution nodeExecution = prepareNodeExecutionForInvocation(ambiance, targetStatus, node);
    if (nodeExecution == null) {
      // This is just for debugging if this is happening then the node status has changed from QUEUED
      // This should never happen
      nodeExecution = nodeExecutionService.get(nodeExecutionId);
      log.warn("Not Starting node execution. Cannot transition from {} to {}", nodeExecution.getStatus(), targetStatus);
      throw new NodeExecutionUpdateFailedException("Cannot Start node Execution");
    }
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
      resolveInputs(ambiance, planNode.getStepInputs(), planNode.isSkipUnresolvedExpressionsCheck());
      return nodeExecutionService.updateStatusWithOps(nodeExecutionId, targetStatus, ops -> {
        setUnset(ops, NodeExecutionKeys.timeoutInstanceIds, timeoutInstanceIds);
      }, EnumSet.noneOf(Status.class));
    });
  }

  private Status calculateStatusFromMode(ExecutionMode executionMode) {
    switch (executionMode) {
      case CONSTRAINT:
        return Status.RESOURCE_WAITING;
      case APPROVAL:
        return Status.APPROVAL_WAITING;
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
      TimeoutParameters timeoutParameters = buildTimeoutParameters(evaluator, timeoutObtainment);
      TimeoutInstance instance =
          timeoutEngine.registerTimeout(timeoutObtainment.getDimension(), timeoutParameters, timeoutCallback);
      timeoutInstanceIds.add(instance.getUuid());
    }
    log.info(format("Registered node execution timeouts: %s", timeoutInstanceIds.toString()));
    return timeoutInstanceIds;
  }

  private TimeoutParameters buildTimeoutParameters(
      EngineExpressionEvaluator evaluator, TimeoutObtainment timeoutObtainment) {
    // TODO (prashant) : Change this this should not be kryo we should trat then exactly like step parameters. Should be
    // json string bytes Evaluate timeout expressions and convert sdk timeout parameters to timeout engine specific
    // parameters.
    SdkTimeoutTrackerParameters sdkTimeoutTrackerParameters =
        (SdkTimeoutTrackerParameters) kryoSerializer.asObject(timeoutObtainment.getParameters().toByteArray());
    sdkTimeoutTrackerParameters = resolve(evaluator, sdkTimeoutTrackerParameters);
    return sdkTimeoutTrackerParameters.prepareTimeoutParameters();
  }

  private <T> T resolve(EngineExpressionEvaluator evaluator, T o) {
    if (o == null) {
      return null;
    }

    Class<?> cls = o.getClass();
    Map<String, Object> m = NodeExecutionUtils.extractObject(RecastOrchestrationUtils.toJson(o));
    String json = RecastOrchestrationUtils.toJson(evaluator.resolve(m, false));
    return (T) RecastOrchestrationUtils.fromJson(json, cls);
  }

  private void resolveInputs(Ambiance ambiance, OrchestrationMap stepInputs, boolean skipUnresolvedCheck) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    log.info("Starting to Resolve step Inputs");
    Object resolvedInputs = pmsEngineExpressionService.resolve(ambiance, stepInputs, skipUnresolvedCheck);
    PmsStepParameters parameterInputs =
        PmsStepParameters.parse(OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(resolvedInputs));
    pmsGraphStepDetailsService.addStepInputs(nodeExecutionId, ambiance.getPlanExecutionId(), parameterInputs);
    log.info("Resolved step Inputs");
  }
}
