/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.advise.AdviseHandlerFactory;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.execution.strategy.AbstractNodeExecutionStrategy;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.IdentityNodeExecutionMetadata;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.logging.AutoLogContext;
import io.harness.plan.IdentityPlanNode;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.start.NodeStartEvent;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.springdata.TransactionHelper;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class IdentityNodeExecutionStrategy
    extends AbstractNodeExecutionStrategy<IdentityPlanNode, IdentityNodeExecutionMetadata> {
  @Inject private PmsEventSender eventSender;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private AdviseHandlerFactory adviseHandlerFactory;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private IdentityNodeResumeHelper identityNodeResumeHelper;
  @Inject private TransactionHelper transactionHelper;
  @Inject private PmsGraphStepDetailsService pmsGraphStepDetailsService;

  private final String SERVICE_NAME_IDENTITY = ModuleType.PMS.name().toLowerCase();

  @Override
  public NodeExecution createNodeExecution(@NotNull Ambiance ambiance, @NotNull IdentityPlanNode node,
      IdentityNodeExecutionMetadata metadata, String notifyId, String parentId, String previousId) {
    String uuid = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution originalExecution = nodeExecutionService.get(node.getOriginalNodeExecutionId());
    NodeExecution execution = NodeExecution.builder()
                                  .uuid(uuid)
                                  .planNode(node)
                                  .ambiance(ambiance)
                                  .levelCount(ambiance.getLevelsCount())
                                  .status(Status.QUEUED)
                                  .unitProgresses(new ArrayList<>())
                                  .startTs(AmbianceUtils.getCurrentLevelStartTs(ambiance))
                                  .originalNodeExecutionId(node.getOriginalNodeExecutionId())
                                  .module(node.getServiceName())
                                  .name(node.getName())
                                  .skipGraphType(node.getSkipGraphType())
                                  .identifier(node.getIdentifier())
                                  .stepType(node.getStepType())
                                  .nodeId(node.getUuid())
                                  .stageFqn(node.getStageFqn())
                                  .group(node.getGroup())
                                  .notifyId(notifyId)
                                  .parentId(parentId)
                                  .previousId(previousId)
                                  .mode(originalExecution.getMode())
                                  .nodeRunInfo(originalExecution.getNodeRunInfo())
                                  .skipInfo(originalExecution.getSkipInfo())
                                  .failureInfo(originalExecution.getFailureInfo())
                                  .progressData(originalExecution.getProgressData())
                                  .adviserResponse(originalExecution.getAdviserResponse())
                                  .timeoutInstanceIds(originalExecution.getTimeoutInstanceIds())
                                  .timeoutDetails(originalExecution.getTimeoutDetails())
                                  .adviserResponse(originalExecution.getAdviserResponse())
                                  .adviserTimeoutInstanceIds(originalExecution.getAdviserTimeoutInstanceIds())
                                  .interruptHistories(originalExecution.getInterruptHistories())
                                  .resolvedParams(originalExecution.getResolvedParams())
                                  .resolvedInputs(originalExecution.getResolvedInputs())
                                  .executionInputConfigured(originalExecution.getExecutionInputConfigured())
                                  .build();
    NodeExecution nodeExecution = nodeExecutionService.save(execution);
    pmsGraphStepDetailsService.copyStepDetailsForRetry(
        ambiance.getPlanExecutionId(), originalExecution.getUuid(), uuid);
    return nodeExecution;
  }

  @Override
  public void startExecution(Ambiance ambiance) {
    String newNodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    NodeExecution newNodeExecution = nodeExecutionService.get(newNodeExecutionId);
    NodeExecution originalExecution = nodeExecutionService.get(newNodeExecution.getOriginalNodeExecutionId());
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      // If Node is skipped then call the adviser response handler straight away
      if (originalExecution.getStatus() == Status.SKIPPED) {
        NodeExecution skippedExecution = nodeExecutionService.updateStatusWithOps(
            newNodeExecutionId, originalExecution.getStatus(), null, EnumSet.noneOf(Status.class));
        processAdviserResponse(ambiance, skippedExecution.getAdviserResponse());
        return;
      }

      // If this is one of the leaf modes then just clone and copy everything and we should be good
      // This is an optimization/hack to not do any actual work
      if (ExecutionModeUtils.isLeafMode(originalExecution.getMode())) {
        handleLeafNodes(ambiance, newNodeExecution, originalExecution.getStatus());
        return;
      }

      NodeExecution runningExecution = nodeExecutionService.updateStatusWithOps(
          newNodeExecutionId, Status.RUNNING, null, EnumSet.noneOf(Status.class));

      // If not leaf node then we need to call the identity step
      Ambiance modifyAmbiance = IdentityStep.modifyAmbiance(ambiance);
      NodeStartEvent nodeStartEvent = NodeStartEvent.newBuilder()
                                          .setAmbiance(modifyAmbiance)
                                          .setStepParameters(runningExecution.getResolvedStepParametersBytes())
                                          .setMode(runningExecution.getMode())
                                          .build();
      // hard code of service name to PMS
      eventSender.sendEvent(
          modifyAmbiance, nodeStartEvent.toByteString(), PmsEventCategory.NODE_START, SERVICE_NAME_IDENTITY, true);
    } catch (Exception exception) {
      log.error("Exception Occurred in facilitateAndStartStep NodeExecutionId : {}, PlanExecutionId: {}",
          AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @VisibleForTesting
  void handleLeafNodes(Ambiance ambiance, NodeExecution nodeExecution, Status status) {
    transactionHelper.performTransaction(() -> {
      // Copy outcomes
      pmsOutcomeService.cloneForRetryExecution(ambiance, nodeExecution.getOriginalNodeExecutionId());
      // Copy outputs
      pmsSweepingOutputService.cloneForRetryExecution(ambiance, nodeExecution.getOriginalNodeExecutionId());

      return nodeExecutionService.updateStatusWithOps(
          nodeExecution.getUuid(), status, null, EnumSet.noneOf(Status.class));
    });
    processAdviserResponse(ambiance, nodeExecution.getAdviserResponse());
  }

  @Override
  public void processAdviserResponse(Ambiance ambiance, AdviserResponse adviserResponse) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      if (adviserResponse == null || adviserResponse.getType() == AdviseType.UNKNOWN) {
        endNodeExecution(ambiance);
        return;
      }
      log.info("Starting to handle Adviser Response of type: {}", adviserResponse.getType());
      NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
      AdviserResponseHandler adviserResponseHandler = adviseHandlerFactory.obtainHandler(adviserResponse.getType());
      adviserResponseHandler.handleAdvise(nodeExecution, adviserResponse);
    }
  }

  @Override
  public void endNodeExecution(Ambiance ambiance) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution = nodeExecutionService.update(
        nodeExecutionId, ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()));
    if (isNotEmpty(nodeExecution.getNotifyId())) {
      Level level = AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance());
      StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                                .nodeUuid(level.getSetupId())
                                                .failureInfo(nodeExecution.getFailureInfo())
                                                .identifier(level.getIdentifier())
                                                .status(nodeExecution.getStatus())
                                                .nodeExecutionId(level.getRuntimeId())
                                                .adviserResponse(nodeExecution.getAdviserResponse())
                                                .build();
      waitNotifyEngine.doneWith(nodeExecution.getNotifyId(), responseData);
    } else {
      log.info("Ending Execution");
      orchestrationEngine.endNodeExecution(AmbianceUtils.cloneForFinish(ambiance));
    }
  }

  @Override
  public void handleError(Ambiance ambiance, Exception exception) {}

  @Override
  public void resumeNodeExecution(Ambiance ambiance, Map<String, ByteString> response, boolean asyncError) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForResume);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      identityNodeResumeHelper.resume(nodeExecution, response, asyncError, SERVICE_NAME_IDENTITY);
    } catch (Exception exception) {
      log.error("Exception Occurred in handling resume with nodeExecutionId {} planExecutionId {}", nodeExecutionId,
          ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void processStepResponse(Ambiance ambiance, StepResponseProto stepResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      NodeExecution newNodeExecution = nodeExecutionService.updateStatusWithOps(
          nodeExecutionId, stepResponse.getStatus(), null, EnumSet.noneOf(Status.class));
      processAdviserResponse(ambiance, newNodeExecution.getAdviserResponse());
    } catch (Exception ex) {
      log.error("Exception Occurred in handleStepResponse NodeExecutionId : {}, PlanExecutionId: {}", nodeExecutionId,
          ambiance.getPlanExecutionId(), ex);
      handleError(ambiance, ex);
    }
  }

  @Override
  public void concludeExecution(
      Ambiance ambiance, Status toStatus, Status fromStatus, EnumSet<Status> overrideStatusSet) {
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(ambiance));

    //      NodeExecution updatedNodeExecution =
    //              nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus, null, overrideStatusSet);
    //      if (updatedNodeExecution == null) {
    //        log.warn("Cannot conclude node execution. Status update failed To:{}", toStatus);
    //        return;
    //      }
    //      endNodeExecution(updatedNodeExecution.getAmbiance());
    //      return;

    nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus, null, EnumSet.noneOf(Status.class));
    endNodeExecution(ambiance);
    // throw new UnsupportedOperationException("Start execution node Supported for plan");
  }
}
