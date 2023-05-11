/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.RetryInterruptConfig;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class RetryHelper {
  @Inject private PlanService planService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private OrchestrationEngine engine;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject private ExecutionInputService executionInputService;
  public void retryNodeExecution(String nodeExecutionId, String interruptId, InterruptConfig interruptConfig) {
    NodeExecution nodeExecution = Preconditions.checkNotNull(nodeExecutionService.get(nodeExecutionId));
    Node node = planService.fetchNode(nodeExecution.getPlanId(), nodeExecution.getNodeId());
    String newUuid = generateUuid();

    Ambiance oldAmbiance = nodeExecution.getAmbiance();
    NodeExecution updatedRetriedNode = updateRetriedNodeMetadata(nodeExecution);

    Level currentLevel = AmbianceUtils.obtainCurrentLevel(oldAmbiance);
    Ambiance ambiance = AmbianceUtils.cloneForFinish(oldAmbiance);
    int newRetryIndex = currentLevel != null ? currentLevel.getRetryIndex() + 1 : 0;
    Ambiance finalAmbiance =
        ambiance.toBuilder()
            .addLevels(PmsLevelUtils.buildLevelFromNode(newUuid, newRetryIndex, node,
                currentLevel.getStrategyMetadata(), ambiance.getMetadata().getUseMatrixFieldName()))
            .build();
    NodeExecution newNodeExecution =
        cloneForRetry(updatedRetriedNode, newUuid, finalAmbiance, interruptConfig, interruptId);
    NodeExecution savedNodeExecution = nodeExecutionService.save(newNodeExecution);

    nodeExecutionService.updateRelationShipsForRetryNode(updatedRetriedNode.getUuid(), savedNodeExecution.getUuid());
    nodeExecutionService.markRetried(updatedRetriedNode.getUuid());
    // Todo: Check with product if we want to stop again for execution time input
    executorService.submit(() -> engine.startNodeExecution(finalAmbiance));
  }

  private NodeExecution updateRetriedNodeMetadata(NodeExecution nodeExecution) {
    NodeExecution updatedNodeExecution = updateRetriedNodeStatusIfInterventionWaiting(nodeExecution);
    if (updatedNodeExecution != null && updatedNodeExecution.getEndTs() == null) {
      updatedNodeExecution = nodeExecutionService.update(
          updatedNodeExecution.getUuid(), ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()));
    }
    return updatedNodeExecution == null ? nodeExecution : updatedNodeExecution;
  }

  // Update the status of older retried node to true status from interventionWaiting if retry is on intervention waiting
  // node.
  private NodeExecution updateRetriedNodeStatusIfInterventionWaiting(NodeExecution nodeExecution) {
    if (nodeExecution.getStatus() == Status.INTERVENTION_WAITING
        && nodeExecution.getAdviserResponse().hasInterventionWaitAdvise()) {
      InterventionWaitAdvise interventionWaitAdvise = nodeExecution.getAdviserResponse().getInterventionWaitAdvise();
      NodeExecution updatedNodeExecution =
          nodeExecutionService.updateStatusWithOps(nodeExecution.getUuid(), interventionWaitAdvise.getFromStatus(),
              ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()), EnumSet.noneOf(Status.class));
      if (updatedNodeExecution == null) {
        log.warn("Cannot conclude node execution. Status update failed From :{}, To:{}", nodeExecution.getStatus(),
            interventionWaitAdvise.getFromStatus());
      }
      return updatedNodeExecution;
    }
    return nodeExecution;
  }

  @VisibleForTesting
  NodeExecution cloneForRetry(NodeExecution nodeExecution, String newUuid, Ambiance ambiance,
      InterruptConfig interruptConfig, String interruptId) {
    List<String> retryIds = isEmpty(nodeExecution.getRetryIds()) ? new LinkedList<>() : nodeExecution.getRetryIds();
    retryIds.add(nodeExecution.getUuid());
    InterruptConfig newInterruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(interruptConfig.getIssuedBy())
            .setRetryInterruptConfig(RetryInterruptConfig.newBuilder().setRetryId(nodeExecution.getUuid()).build())
            .build();
    InterruptEffect interruptEffect = InterruptEffect.builder()
                                          .interruptType(InterruptType.RETRY)
                                          .tookEffectAt(System.currentTimeMillis())
                                          .interruptId(interruptId)
                                          .interruptConfig(newInterruptConfig)
                                          .build();

    List<InterruptEffect> interruptHistories =
        isEmpty(nodeExecution.getInterruptHistories()) ? new LinkedList<>() : nodeExecution.getInterruptHistories();
    interruptHistories.add(interruptEffect);
    cloneAndSaveInputInstanceForRetry(nodeExecution.getUuid(), newUuid);

    return NodeExecution.builder()
        .uuid(newUuid)
        .ambiance(ambiance)
        .planNode(nodeExecution.getNode())
        .levelCount(ambiance.getLevelsCount())
        .mode(null)
        .endTs(null)
        .initialWaitDuration(null)
        .resolvedStepParameters(null)
        .resolvedParams(nodeExecution.getResolvedParams())
        .notifyId(nodeExecution.getNotifyId())
        .parentId(nodeExecution.getParentId())
        .nextId(nodeExecution.getNextId())
        .previousId(nodeExecution.getPreviousId())
        .lastUpdatedAt(null)
        .version(null)
        .executableResponses(new ArrayList<>())
        .interruptHistories(interruptHistories)
        .failureInfo(null)
        .status(Status.QUEUED)
        .timeoutInstanceIds(new ArrayList<>())
        .timeoutDetails(null)
        .retryIds(retryIds)
        .oldRetry(false)
        .originalNodeExecutionId(nodeExecution.getOriginalNodeExecutionId())
        .module(nodeExecution.getModule())
        .name(nodeExecution.getName())
        .skipGraphType(nodeExecution.getSkipGraphType())
        .identifier(nodeExecution.getIdentifier())
        .stepType(nodeExecution.getStepType())
        .nodeId(nodeExecution.getNodeId())
        .stageFqn(nodeExecution.getStageFqn())
        .group(nodeExecution.getGroup())
        .build();
  }

  @VisibleForTesting
  ExecutionInputInstance cloneAndSaveInputInstanceForRetry(String originalNodeExecutionId, String newNodeExecutionId) {
    ExecutionInputInstance inputInstance = executionInputService.getExecutionInputInstance(originalNodeExecutionId);
    if (inputInstance == null) {
      log.info("ExecutionInput instance is null for nodeExecutionId: {}", originalNodeExecutionId);
      return null;
    }
    inputInstance.setInputInstanceId(UUIDGenerator.generateUuid());
    inputInstance.setNodeExecutionId(newNodeExecutionId);
    return executionInputService.save(inputInstance);
  }
}
