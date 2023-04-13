/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plannode;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.contracts.execution.Status.RUNNING;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.execution.WaitForExecutionInputHelper;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.facilitation.FacilitationHelper;
import io.harness.engine.facilitation.RunPreFacilitationChecker;
import io.harness.engine.facilitation.SkipPreFacilitationChecker;
import io.harness.engine.facilitation.facilitator.publisher.FacilitateEventPublisher;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.pms.advise.AdviseHandlerFactory;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.execution.strategy.AbstractNodeExecutionStrategy;
import io.harness.engine.pms.execution.strategy.EndNodeExecutionHelper;
import io.harness.engine.pms.resume.NodeResumeHelper;
import io.harness.engine.pms.start.NodeStartHelper;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionMetadata;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.logging.AutoLogContext;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.EngineExceptionUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanNodeExecutionStrategy extends AbstractNodeExecutionStrategy<PlanNode, NodeExecutionMetadata> {
  @Inject private Injector injector;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private FacilitationHelper facilitationHelper;
  @Inject private ExceptionManager exceptionManager;
  @Inject private EndNodeExecutionHelper endNodeExecutionHelper;
  @Inject private NodeAdviseHelper nodeAdviseHelper;
  @Inject private FacilitateEventPublisher facilitateEventPublisher;
  @Inject private NodeStartHelper startHelper;
  @Inject private InterruptService interruptService;
  @Inject private AdviseHandlerFactory adviseHandlerFactory;
  @Inject private NodeResumeHelper resumeHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PmsOutcomeService outcomeService;
  @Inject private KryoSerializer kryoSerializer;

  @Inject private PlanExpansionService planExpansionService;

  @Inject @Named("EngineExecutorService") ExecutorService executorService;
  @Inject WaitForExecutionInputHelper waitForExecutionInputHelper;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject PlanExecutionService planExecutionService;
  @Inject TransactionHelper transactionHelper;

  @Override
  public NodeExecution createNodeExecution(@NotNull Ambiance ambiance, @NotNull PlanNode node,
      NodeExecutionMetadata metadata, String notifyId, String parentId, String previousId) {
    String uuid = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(uuid)
            .planNode(node)
            .executionInputConfigured(!EmptyPredicate.isEmpty(node.getExecutionInputTemplate()))
            .ambiance(ambiance)
            .levelCount(ambiance.getLevelsCount())
            .status(Status.QUEUED)
            .notifyId(notifyId)
            .parentId(parentId)
            .previousId(previousId)
            .unitProgresses(new ArrayList<>())
            .module(node.getServiceName())
            .name(AmbianceUtils.modifyIdentifier(ambiance, node.getName()))
            .skipGraphType(node.getSkipGraphType())
            .identifier(AmbianceUtils.modifyIdentifier(ambiance, node.getIdentifier()))
            .stepType(node.getStepType())
            .nodeId(node.getUuid())
            .stageFqn(node.getStageFqn())
            .group(node.getGroup())
            .build();
    return nodeExecutionService.save(nodeExecution);
  }

  @VisibleForTesting
  void resolveParameters(Ambiance ambiance, PlanNode planNode) {
    String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    log.info("Starting to Resolve step parameters");
    Object resolvedStepParameters =
        pmsEngineExpressionService.resolve(ambiance, planNode.getStepParameters(), planNode.getExpressionMode());
    PmsStepParameters resolvedParameters = PmsStepParameters.parse(
        OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(resolvedStepParameters));

    transactionHelper.performTransaction(() -> {
      // TODO (prashant) : This is a hack right now to serialize in binary as findAndModify is not honoring converter
      // for maps Find a better way to do this
      nodeExecutionService.updateV2(nodeExecutionId,
          ops -> ops.set(NodeExecutionKeys.resolvedParams, kryoSerializer.asDeflatedBytes(resolvedParameters)));
      planExpansionService.addStepInputs(ambiance, resolvedParameters);
      return resolvedParameters;
    });
    log.info("Resolved to step parameters");
  }

  @Override
  public void startExecution(Ambiance ambiance) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String nodeId = AmbianceUtils.obtainCurrentSetupId(ambiance);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      PlanNode planNode = planService.fetchNode(ambiance.getPlanId(), nodeId);
      resolveParameters(ambiance, planNode);

      ExecutionCheck check = performPreFacilitationChecks(ambiance, planNode);
      if (!check.isProceed()) {
        log.info("Not Proceeding with  Execution. Reason : {}", check.getReason());
        return;
      }
      log.info("Proceeding with  Execution. Reason : {}", check.getReason());

      if (waitForExecutionInputHelper.waitForExecutionInput(ambiance, nodeExecutionId, planNode)) {
        return;
      }
      if (facilitationHelper.customFacilitatorPresent(planNode)) {
        facilitateEventPublisher.publishEvent(ambiance, planNode);
        return;
      }
      FacilitatorResponseProto facilitatorResponseProto =
          facilitationHelper.calculateFacilitatorResponse(ambiance, planNode);
      processFacilitationResponse(ambiance, facilitatorResponseProto);
    } catch (Exception exception) {
      log.error("Exception Occurred in facilitateAndStartStep NodeExecutionId : {}, PlanExecutionId: {}",
          nodeExecutionId, ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void processFacilitationResponse(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      nodeExecutionService.updateV2(
          nodeExecutionId, ops -> ops.set(NodeExecutionKeys.mode, facilitatorResponse.getExecutionMode()));
      ExecutionCheck check = interruptService.checkInterruptsPreInvocation(
          ambiance.getPlanExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      if (!check.isProceed()) {
        log.info("Not Proceeding with Execution : {}", check.getReason());
        return;
      }
      startHelper.startNode(ambiance, facilitatorResponse);
    } catch (Exception exception) {
      log.error("Exception Occurred while processing facilitation response NodeExecutionId : {}, PlanExecutionId: {}",
          AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void processStartEventResponse(Ambiance ambiance, ExecutableResponse executableResponse) {}

  @Override
  public void resumeNodeExecution(Ambiance ambiance, Map<String, ResponseDataProto> response, boolean asyncError) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForResume);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      if (!StatusUtils.resumableStatuses().contains(nodeExecution.getStatus())) {
        log.warn("NodeExecution is no longer in RESUMABLE state Uuid: {} Status {} ", nodeExecution.getUuid(),
            nodeExecution.getStatus());
        return;
      }
      if (nodeExecution.getStatus() != RUNNING) {
        log.info("Marking the nodeExecution with id {} as RUNNING", nodeExecutionId);
        nodeExecution = Preconditions.checkNotNull(
            nodeExecutionService.updateStatusWithOps(nodeExecutionId, RUNNING, null, EnumSet.noneOf(Status.class)));
        // After resuming, pipeline status need to be set. Ex: Pipeline waiting on approval step, pipeline status is
        // waiting, after approval, node execution is marked as running and,  similarly we are marking for pipeline.
        // Earlier pipeline status was marked from step itself.

        // Please refer the explanation added above the method - calculateAndUpdateRunningStatus(
        planExecutionService.calculateAndUpdateRunningStatus(ambiance.getPlanExecutionId(), nodeExecutionId);
      } else {
        // This will happen if the node is not in any paused or waiting statuses.
        log.debug("NodeExecution with id {} is already in Running status", nodeExecutionId);
      }
      resumeHelper.resume(nodeExecution, response, asyncError);
    } catch (Exception exception) {
      log.error("Exception Occurred in handling resume with nodeExecutionId {} planExecutionId {}", nodeExecutionId,
          ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void concludeExecution(
      Ambiance ambiance, Status toStatus, Status fromStatus, EnumSet<Status> overrideStatusSet) {
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(ambiance));
    PlanNode node = planService.fetchNode(ambiance.getPlanId(), level.getSetupId());
    if (isEmpty(node.getAdviserObtainments())) {
      NodeExecution updatedNodeExecution =
          nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus, null, overrideStatusSet);
      if (updatedNodeExecution == null) {
        log.warn("Cannot conclude node execution. Status update failed To:{}", toStatus);
        return;
      }
      endNodeExecution(updatedNodeExecution.getAmbiance());
      return;
    }
    NodeExecution updatedNodeExecution = nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus,
        ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()), overrideStatusSet);
    if (updatedNodeExecution == null) {
      log.warn("Cannot conclude node execution. Status update failed To:{}", toStatus);
      return;
    }
    nodeAdviseHelper.queueAdvisingEvent(updatedNodeExecution, node, fromStatus);
  }

  @Override
  public void processStepResponse(Ambiance ambiance, StepResponseProto stepResponse) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      handleStepResponseInternal(ambiance, stepResponse);
    } catch (Exception ex) {
      log.error("Exception Occurred in handleStepResponse NodeExecutionId : {}, PlanExecutionId: {}",
          AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId(), ex);
      handleError(ambiance, ex);
    }
  }

  @Override
  public void processAdviserResponse(Ambiance ambiance, AdviserResponse adviserResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      if (adviserResponse.getType() == AdviseType.UNKNOWN) {
        log.warn("Got null advise for node execution with id {}", nodeExecutionId);
        endNodeExecution(ambiance);
        return;
      }
      log.info("Starting to handle Adviser Response of type: {}", adviserResponse.getType());
      // Get all fields of NodeExecution as advisors may use any fields of NodeExecution
      NodeExecution updatedNodeExecution = nodeExecutionService.update(
          nodeExecutionId, ops -> ops.set(NodeExecutionKeys.adviserResponse, adviserResponse));
      AdviserResponseHandler adviserResponseHandler = adviseHandlerFactory.obtainHandler(adviserResponse.getType());
      adviserResponseHandler.handleAdvise(updatedNodeExecution, adviserResponse);
    }
  }

  @Override
  public void endNodeExecution(Ambiance ambiance) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
      NodeExecution nodeExecution =
          nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForExecutionStrategy);
      if (isNotEmpty(nodeExecution.getNotifyId())) {
        Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
        StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                                  .nodeUuid(level.getSetupId())
                                                  .stepOutcomeRefs(outcomeService.fetchOutcomeRefs(nodeExecutionId))
                                                  .failureInfo(nodeExecution.getFailureInfo())
                                                  .identifier(level.getIdentifier())
                                                  .nodeExecutionId(level.getRuntimeId())
                                                  .status(nodeExecution.getStatus())
                                                  .adviserResponse(nodeExecution.getAdviserResponse())
                                                  .nodeExecutionEndTs(nodeExecution.getEndTs())
                                                  .build();
        waitNotifyEngine.doneWith(nodeExecution.getNotifyId(), responseData);
      } else {
        log.info("Ending Execution");
        orchestrationEngine.endNodeExecution(AmbianceUtils.cloneForFinish(ambiance));
      }
    }
  }

  @VisibleForTesting
  void handleStepResponseInternal(@NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse) {
    PlanNode planNode = planService.fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    if (isEmpty(planNode.getAdviserObtainments())) {
      log.info("No Advisers for the node Ending Execution");
      endNodeExecutionHelper.endNodeExecutionWithNoAdvisers(ambiance, stepResponse);
      return;
    }
    // TODO: find a way to remove this and pass old status as parameter
    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
        AmbianceUtils.obtainCurrentRuntimeId(ambiance), NodeProjectionUtils.withStatus);
    NodeExecution updatedNodeExecution = endNodeExecutionHelper.handleStepResponsePreAdviser(ambiance, stepResponse);
    if (updatedNodeExecution == null) {
      return;
    }

    nodeAdviseHelper.queueAdvisingEvent(updatedNodeExecution, planNode, nodeExecution.getStatus());
  }

  @VisibleForTesting
  ExecutionCheck performPreFacilitationChecks(Ambiance ambiance, PlanNode planNode) {
    // Ignore facilitation checks if node is retried

    if (AmbianceUtils.isRetry(ambiance)) {
      return ExecutionCheck.builder().proceed(true).reason("Node is retried.").build();
    }
    RunPreFacilitationChecker rChecker = injector.getInstance(RunPreFacilitationChecker.class);
    SkipPreFacilitationChecker sChecker = injector.getInstance(SkipPreFacilitationChecker.class);
    rChecker.setNextChecker(sChecker);
    return rChecker.check(ambiance, planNode);
  }

  @Override
  public void handleError(Ambiance ambiance, Exception exception) {
    try {
      StepResponseProto.Builder builder = StepResponseProto.newBuilder().setStatus(Status.FAILED);
      List<ResponseMessage> responseMessages = exceptionManager.buildResponseFromException(exception);
      if (isNotEmpty(responseMessages)) {
        builder.setFailureInfo(EngineExceptionUtils.transformResponseMessagesToFailureInfo(responseMessages));
      }
      handleStepResponseInternal(ambiance, builder.build());
    } catch (Exception ex) {
      // Smile if you see irony in this
      log.error("This is very BAD!!!. Exception Occurred while handling Exception. Erroring out Execution", ex);
    }
  }
}
