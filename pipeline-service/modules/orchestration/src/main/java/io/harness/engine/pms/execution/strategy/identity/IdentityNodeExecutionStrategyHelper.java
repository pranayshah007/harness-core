/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.exception.UnexpectedException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class IdentityNodeExecutionStrategyHelper {
  @Inject private PmsGraphStepDetailsService pmsGraphStepDetailsService;
  @Inject private NodeExecutionService nodeExecutionService;

  public NodeExecution createNodeExecution(
      @NotNull Ambiance ambiance, @NotNull IdentityPlanNode node, String notifyId, String parentId, String previousId) {
    String uuid = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution originalExecution;
    if (EmptyPredicate.isEmpty(node.getOriginalNodeExecutionId())) {
      CloseableIterator<NodeExecution> nodeExecutions = nodeExecutionService.get(node.getAllOriginalNodeExecutionIds());
      originalExecution = getCorrectNodeExecution(nodeExecutions, ambiance.getLevelsList());
    } else {
      originalExecution = nodeExecutionService.get(node.getOriginalNodeExecutionId());
    }
    NodeExecution execution = NodeExecution.builder()
                                  .uuid(uuid)
                                  .planNode(node)
                                  .ambiance(ambiance)
                                  .levelCount(ambiance.getLevelsCount())
                                  .status(Status.QUEUED)
                                  .unitProgresses(new ArrayList<>())
                                  .startTs(AmbianceUtils.getCurrentLevelStartTs(ambiance))
                                  .originalNodeExecutionId(originalExecution.getUuid())
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

  // if a list of node execution IDs is provided, the strategy metadata at all levels currently should match the
  // strategy metadata in the selected node execution
  NodeExecution getCorrectNodeExecution(CloseableIterator<NodeExecution> nodeExecutions, List<Level> currLevels) {
    List<StrategyMetadata> strategyMetadata =
        currLevels.stream().map(Level::getStrategyMetadata).collect(Collectors.toList());
    while (nodeExecutions.hasNext()) {
      NodeExecution nodeExecution = nodeExecutions.next();
      List<StrategyMetadata> currNodeStrategyMetadata = nodeExecution.getAmbiance()
                                                            .getLevelsList()
                                                            .stream()
                                                            .map(Level::getStrategyMetadata)
                                                            .collect(Collectors.toList());
      if (currNodeStrategyMetadata.equals(strategyMetadata)) {
        return nodeExecution;
      }
    }
    throw new UnexpectedException(
        "None of the fetched node executions matched the required levels. Current strategy levels: "
        + strategyMetadata);
  }

  // Cloning the nodeExecution. Also copying the original retryIds. We will update the retryIds later in the caller
  // method.
  NodeExecutionBuilder cloneNodeExecutionForRetries(NodeExecution originalNodeExecution, Ambiance ambiance) {
    String uuid = UUIDGenerator.generateUuid();
    Ambiance finalAmbiance = AmbianceUtils.cloneForFinish(ambiance)
                                 .toBuilder()
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(uuid, originalNodeExecution.getNode()))
                                 .build();

    String parentId = AmbianceUtils.obtainParentRuntimeId(finalAmbiance);
    String notifyId = parentId == null ? null : AmbianceUtils.obtainCurrentRuntimeId(finalAmbiance);

    Node node = originalNodeExecution.getNode();
    return NodeExecution.builder()
        .uuid(uuid)
        .planNode(node)
        .ambiance(finalAmbiance)
        .oldRetry(true)
        .retryIds(originalNodeExecution.getRetryIds())
        .executableResponses(originalNodeExecution.getExecutableResponses())
        .levelCount(finalAmbiance.getLevelsCount())
        .status(originalNodeExecution.getStatus())
        .unitProgresses(new ArrayList<>())
        .createdAt(AmbianceUtils.getCurrentLevelStartTs(finalAmbiance))
        .startTs(AmbianceUtils.getCurrentLevelStartTs(finalAmbiance))
        .endTs(System.currentTimeMillis())
        .originalNodeExecutionId(originalNodeExecution.getUuid())
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
        .mode(originalNodeExecution.getMode())
        .nodeRunInfo(originalNodeExecution.getNodeRunInfo())
        .failureInfo(originalNodeExecution.getFailureInfo())
        .progressData(originalNodeExecution.getProgressData())
        .adviserResponse(originalNodeExecution.getAdviserResponse())
        .timeoutInstanceIds(originalNodeExecution.getTimeoutInstanceIds())
        .timeoutDetails(originalNodeExecution.getTimeoutDetails())
        .adviserResponse(originalNodeExecution.getAdviserResponse())
        .adviserTimeoutInstanceIds(originalNodeExecution.getAdviserTimeoutInstanceIds())
        .resolvedParams(originalNodeExecution.getResolvedParams())
        .resolvedInputs(originalNodeExecution.getResolvedInputs())
        .executionInputConfigured(originalNodeExecution.getExecutionInputConfigured())
        .interruptHistories(originalNodeExecution.getInterruptHistories());
  }

  // Update retryIds in retryInterruptConfigs to point to new nodeExecutions if it was pointing to original
  // nodeExecution.
  @VisibleForTesting
  List<InterruptEffect> getUpdatedInterruptHistory(
      List<InterruptEffect> originalInterruptHistory, Map<String, String> originalRetryIdToNewRetryIdMap) {
    List<InterruptEffect> newInterruptHistory = new ArrayList<>();
    for (InterruptEffect interruptEffect : originalInterruptHistory) {
      if (interruptEffect.getInterruptConfig().hasRetryInterruptConfig()) {
        io.harness.pms.contracts.interrupts.RetryInterruptConfig retryInterruptConfig =
            interruptEffect.getInterruptConfig().getRetryInterruptConfig();
        String newRetryId = originalRetryIdToNewRetryIdMap.get(retryInterruptConfig.getRetryId());
        if (newRetryId == null) {
          continue;
        }
        retryInterruptConfig = retryInterruptConfig.toBuilder().setRetryId(newRetryId).build();
        newInterruptHistory.add(InterruptEffect.builder()
                                    .interruptConfig(interruptEffect.getInterruptConfig()
                                                         .toBuilder()
                                                         .setRetryInterruptConfig(retryInterruptConfig)
                                                         .build())
                                    .interruptId(interruptEffect.getInterruptId())
                                    .interruptType(interruptEffect.getInterruptType())
                                    .tookEffectAt(interruptEffect.getTookEffectAt())
                                    .build());
      } else {
        newInterruptHistory.add(interruptEffect);
      }
    }
    return newInterruptHistory;
  }

  // Copying the nodeExecutions for retried nodes. Will create clone nodeExecution for each retried NodeExecution and
  // update retriedIds with newly created NodeExecutions.
  @VisibleForTesting
  void copyNodeExecutionsForRetriedNodes(NodeExecution nodeExecution, List<String> originalOldRetryIds) {
    if (EmptyPredicate.isEmpty(originalOldRetryIds)) {
      return;
    }
    Map<String, String> originalRetryIdToNewRetryIdMap = new HashMap<>();
    List<NodeExecutionBuilder> clonedNodeExecutions = new ArrayList<>();

    List<NodeExecution> nodeExecutions = nodeExecutionService.getAll(new HashSet<>(originalOldRetryIds));

    for (NodeExecution originalNodeExecution : nodeExecutions) {
      NodeExecutionBuilder newNodeExecution =
          cloneNodeExecutionForRetries(originalNodeExecution, nodeExecution.getAmbiance());
      clonedNodeExecutions.add(newNodeExecution);
      originalRetryIdToNewRetryIdMap.put(originalNodeExecution.getUuid(), newNodeExecution.build().getUuid());
    }

    for (NodeExecutionBuilder clonedNodeExecutionBuilder : clonedNodeExecutions) {
      clonedNodeExecutionBuilder.retryIds(getNewRetryIdsFromOriginalRetryIds(
          clonedNodeExecutionBuilder.build().getRetryIds(), originalRetryIdToNewRetryIdMap));
      clonedNodeExecutionBuilder.interruptHistories(getUpdatedInterruptHistory(
          clonedNodeExecutionBuilder.build().getInterruptHistories(), originalRetryIdToNewRetryIdMap));
    }
    nodeExecutionService.saveAll(
        clonedNodeExecutions.stream().map(NodeExecutionBuilder::build).collect(Collectors.toList()));
    updateFinalRetriedNode(nodeExecution.getUuid(), nodeExecution.getInterruptHistories(), originalOldRetryIds,
        originalRetryIdToNewRetryIdMap);
  }

  private void updateFinalRetriedNode(String nodeExecutionId, List<InterruptEffect> interruptHistories,
      List<String> originalRetryIds, Map<String, String> originalRetryIdToNewRetryIdMap) {
    List<String> finalNodeRetryIds =
        getNewRetryIdsFromOriginalRetryIds(originalRetryIds, originalRetryIdToNewRetryIdMap);
    List<InterruptEffect> finalInterruptHistory =
        getUpdatedInterruptHistory(interruptHistories, originalRetryIdToNewRetryIdMap);
    nodeExecutionService.updateV2(nodeExecutionId, update -> {
      update.set(NodeExecutionKeys.retryIds, finalNodeRetryIds);
      update.set(NodeExecutionKeys.interruptHistories, finalInterruptHistory);
      update.set(NodeExecutionKeys.startTs, System.currentTimeMillis());
    });
  }

  // This method returns list of new retry Ids corresponding to original retry ids.
  @VisibleForTesting
  List<String> getNewRetryIdsFromOriginalRetryIds(
      List<String> originalRetryIds, Map<String, String> originalRetryIdToNewRetryIdMap) {
    return originalRetryIds.stream().map(originalRetryIdToNewRetryIdMap::get).collect(Collectors.toList());
  }
}
