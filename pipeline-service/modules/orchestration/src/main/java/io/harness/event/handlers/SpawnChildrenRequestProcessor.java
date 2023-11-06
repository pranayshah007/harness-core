/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.OrchestrationPublisherName;
import io.harness.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.concurrency.MaxConcurrentChildCallback;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.observers.BarrierExpandObserver;
import io.harness.engine.observers.BarrierExpandRequest;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.execution.InitiateNodeHelper;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.logging.AutoLogContext;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class SpawnChildrenRequestProcessor implements SdkResponseProcessor {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private InitiateNodeHelper initiateNodeHelper;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;
  @Inject @Getter private final Subject<BarrierExpandObserver> barrierWithinStrategyExpander = new Subject<>();

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    SpawnChildrenRequest request = event.getSpawnChildrenRequest();
    Ambiance ambiance = event.getAmbiance();
    String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    String nodeSetupId = Objects.requireNonNull(AmbianceUtils.obtainCurrentSetupId(ambiance));
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      List<String> childrenIds = new ArrayList<>();
      List<String> callbackIds = new ArrayList<>();
      int currentChild = 0;
      for (int i = 0; i < request.getChildren().getChildrenList().size(); i++) {
        childrenIds.add(generateUuid());
      }
      int maxConcurrency = getMaxConcurrencyLimit(ambiance, childrenIds, request.getChildren().getMaxConcurrency());
      if (AmbianceUtils.checkIfFeatureFlagEnabled(
              ambiance, FeatureName.CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES.name())) {
        expandBarriersWithinStrategyNode(nodeExecutionId, nodeSetupId,
            request.getChildren().getChildrenList().stream().map(Child::getChildNodeId).collect(Collectors.toList()),
            childrenIds, ambiance, maxConcurrency);
      }

      List<Child> filteredChildren = getFilteredChildren(ambiance, request.getChildren().getChildrenList());
      if (childrenIds.isEmpty() || filteredChildren.isEmpty()) {
        // If callbackIds are empty then it means that there are no children, we should just do a no-op and return to
        // parent.
        orchestrationEngine.resumeNodeExecution(ambiance, new HashMap<>(), false);
        return;
      }

      // Save the ConcurrentChildInstance in db first so that whenever callback is called, this information is readily
      // available. If not done here, it could lead to race conditions
      nodeExecutionInfoService.addConcurrentChildInformation(
          ConcurrentChildInstance.builder().childrenNodeExecutionIds(childrenIds).cursor(maxConcurrency).build(),
          nodeExecutionId);

      List<Ambiance> ambianceList = new ArrayList<>();
      for (Child child : filteredChildren) {
        String uuid = childrenIds.get(currentChild);
        StrategyMetadata strategyMetadata = child.hasStrategyMetadata() ? child.getStrategyMetadata() : null;
        callbackIds.add(uuid);
        NodeExecution nodeExecution = orchestrationEngine.initiateNode(
            ambiance, child.getChildNodeId(), uuid, null, strategyMetadata, InitiateMode.CREATE);
        if (shouldCreateAndStart(maxConcurrency, currentChild)) {
          if (nodeExecution != null) {
            ambianceList.add(nodeExecution.getAmbiance());
          }
        }
        // We should register MaxConcurrentChildCallback only when we will use max concurrency.
        // If there is no need to have concurrency, we should avoid adding callbacks.
        if (filteredChildren.size() > maxConcurrency) {
          MaxConcurrentChildCallback maxConcurrentChildCallback =
              MaxConcurrentChildCallback.builder()
                  .parentNodeExecutionId(nodeExecutionId)
                  .planExecutionId(ambiance.getPlanExecutionId())
                  .maxConcurrency(maxConcurrency)
                  .proceedIfFailed(request.getChildren().getShouldProceedIfFailed())
                  .build();

          String waitInstanceId = waitNotifyEngine.waitForAllOn(publisherName, maxConcurrentChildCallback, uuid);
          log.info("SpawnChildrenRequestProcessor registered a waitInstance for maxConcurrency with waitInstanceId: {}",
              waitInstanceId);
        }
        currentChild++;
      }

      for (Ambiance ambianceentry : ambianceList) {
        initiateNodeHelper.publishEvent(ambianceentry, InitiateMode.START);
      }

      if (callbackIds.isEmpty()) {
        orchestrationEngine.resumeNodeExecution(ambiance, new HashMap<>(), false);
        return;
      }

      // If some children were skipped due to rollback mode. Then update the concurrent children info.
      if (callbackIds.size() < childrenIds.size()) {
        nodeExecutionInfoService.addConcurrentChildInformation(
            ConcurrentChildInstance.builder().childrenNodeExecutionIds(callbackIds).cursor(maxConcurrency).build(),
            nodeExecutionId);
      }

      // Attach a Callback to the parent for the child
      EngineResumeCallback callback = EngineResumeCallback.builder().ambiance(ambiance).build();
      String waitInstanceId =
          waitNotifyEngine.waitForAllOn(publisherName, callback, callbackIds.toArray(new String[0]));
      log.info("SpawnChildrenRequestProcessor registered a waitInstance with id: {}", waitInstanceId);

      // Update the parent with executable response
      nodeExecutionService.updateV2(nodeExecutionId,
          ops
          -> ops.addToSet(NodeExecutionKeys.executableResponses,
              ExecutableResponse.newBuilder().setChildren(request.getChildren()).build()));
    }
  }

  /**
   * Get max concurrency limit based on plan and pipeline setting.
   * If maxConcurrency provided is less than the limit, we use the max concurrency provided by the user.
   */
  private int getMaxConcurrencyLimit(Ambiance ambiance, List<String> childrenIds, long requestMaxConcurrency) {
    int maxConcurrencyLimit = pipelineSettingsService.getMaxConcurrencyBasedOnEdition(
        AmbianceUtils.getAccountId(ambiance), childrenIds.size());
    int maxConcurrency = maxConcurrencyLimit;
    if (requestMaxConcurrency > 0 && requestMaxConcurrency < maxConcurrencyLimit) {
      maxConcurrency = (int) requestMaxConcurrency;
    }
    return maxConcurrency;
  }

  /**
   * This filters the children provided by strategy node.
   *
   * Filtering is required mainly for post prod rollback because
   *  - We need to run only one combination in matrix which deployed that service.
   *  - If the service being rolled back is not inside matrix, then we want to do a
   *  no-op
   */
  @VisibleForTesting
  List<Child> getFilteredChildren(Ambiance ambiance, List<Child> children) {
    // Calculating children only when strategy is at stage level - AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance)
    if (ambiance.getMetadata().getExecutionMode() == ExecutionMode.POST_EXECUTION_ROLLBACK
        && AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance)) {
      List<PostExecutionRollbackInfo> postExecutionRollbackInfos = getPostExecutionRollbackInfo(ambiance);
      Multimap<String, StrategyMetadata> strategyMetadataMap = HashMultimap.create();
      postExecutionRollbackInfos.forEach(
          o -> strategyMetadataMap.put(o.getPostExecutionRollbackStageId(), o.getRollbackStageStrategyMetadata()));
      String parentNodeId = AmbianceUtils.obtainCurrentSetupId(ambiance);
      List<Child> filteredChild = new LinkedList<>();
      // If the parentNodeId is present in the list of stages being rolledBack. Then initiate  we will select the first
      // child and replace the strategyMetaData of the child with the strategyMetadata in postExecutionRollbackInfo
      if (AmbianceUtils.getCurrentStepType(ambiance).getStepCategory() == StepCategory.STRATEGY) {
        if (strategyMetadataMap.containsKey(parentNodeId)) {
          Collection<StrategyMetadata> strategyMetadataList = strategyMetadataMap.get(parentNodeId);
          int count = 0;
          for (StrategyMetadata strategyMetadata : strategyMetadataList) {
            filteredChild.add(Child.newBuilder()
                                  .setChildNodeId(children.get(count).getChildNodeId())
                                  .setStrategyMetadata(strategyMetadata)
                                  .build());
            count++;
            if (count == children.size()) {
              break;
            }
          }
        }
        return filteredChild;
      } else {
        return children;
      }
    }
    return children;
  }

  private List<PostExecutionRollbackInfo> getPostExecutionRollbackInfo(Ambiance ambiance) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
        ambiance.getPlanExecutionId(), PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    // TODO(archit): Remove get from execution_metadata from next release
    if (EmptyPredicate.isEmpty(planExecutionMetadata.getPostExecutionRollbackInfos())) {
      return ambiance.getMetadata().getPostExecutionRollbackInfoList();
    }
    return planExecutionMetadata.getPostExecutionRollbackInfos();
  }

  private boolean shouldCreateAndStart(int maxConcurrency, int currentChild) {
    return currentChild < maxConcurrency;
  }

  private void expandBarriersWithinStrategyNode(String strategyExecutionId, String strategySetupId,
      List<String> childrenSetupIds, List<String> childrenRuntimeIds, Ambiance ambiance, int maxConcurrency) {
    barrierWithinStrategyExpander.fireInform(BarrierExpandObserver::onInitializeRequest,
        BarrierExpandRequest.builder()
            .strategyExecutionId(strategyExecutionId)
            .strategySetupId(strategySetupId)
            .childrenSetupIds(childrenSetupIds)
            .childrenRuntimeIds(childrenRuntimeIds)
            .stageExecutionId(ambiance.getStageExecutionId())
            .planExecutionId(ambiance.getPlanExecutionId())
            .maxConcurrency(maxConcurrency)
            .build());
  }
}
