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
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.concurrency.MaxConcurrentChildCallback;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.execution.InitiateNodeHelper;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class SpawnChildrenRequestProcessor implements SdkResponseProcessor {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private InitiateNodeHelper initiateNodeHelper;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PmsGraphStepDetailsService nodeExecutionInfoService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private PlanService planService;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    SpawnChildrenRequest request = event.getSpawnChildrenRequest();
    Ambiance ambiance = event.getAmbiance();
    String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      List<String> childrenIds = new ArrayList<>();
      List<String> callbackIds = new ArrayList<>();
      int currentChild = 0;
      for (int i = 0; i < request.getChildren().getChildrenList().size(); i++) {
        childrenIds.add(generateUuid());
      }
      int maxConcurrencyLimit = pipelineSettingsService.getMaxConcurrencyBasedOnEdition(
          AmbianceUtils.getAccountId(ambiance), childrenIds.size());
      int maxConcurrency = maxConcurrencyLimit;
      if (request.getChildren().getMaxConcurrency() > 0
          && request.getChildren().getMaxConcurrency() < maxConcurrencyLimit) {
        maxConcurrency = (int) request.getChildren().getMaxConcurrency();
      }

      if (childrenIds.isEmpty()) {
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
      List<PostExecutionRollbackInfo> postExecutionRollbackInfos =
          ambiance.getMetadata().getPostExecutionRollbackInfoList();
      Map<String, StrategyMetadata> strategyMetadataMap = new HashMap<>();
      postExecutionRollbackInfos.forEach(
          o -> strategyMetadataMap.put(o.getPostExecutionRollbackStageId(), o.getRollbackStageStrategyMetadata()));
      String parentNodeId = AmbianceUtils.obtainCurrentSetupId(ambiance);

      for (Child child : request.getChildren().getChildrenList()) {
        String uuid = childrenIds.get(currentChild);
        StrategyMetadata strategyMetadata = child.hasStrategyMetadata() ? child.getStrategyMetadata() : null;

        if (ambiance.getMetadata().getExecutionMode() == ExecutionMode.POST_EXECUTION_ROLLBACK) {
          // If the parentNodeId is present in the list of stages being rolledBack. Then initiate the child only if its
          // strategyMetadata matches the strategyMetadata of stage being rolledBack.
          if (strategyMetadataMap.containsKey(parentNodeId)
              && !strategyMetadataMap.get(parentNodeId).equals(child.getStrategyMetadata())) {
            continue;
          }
        }
        callbackIds.add(uuid);

        // If the current child count is less than maxConcurrency then create and start the nodeExecution
        if (shouldCreateAndStart(maxConcurrency, currentChild)) {
          initiateNodeHelper.publishEvent(
              ambiance, child.getChildNodeId(), uuid, strategyMetadata, InitiateMode.CREATE_AND_START);
        } else {
          // IF the current child count is greater than maxConcurrency then only create the nodeExecution
          orchestrationEngine.initiateNode(
              ambiance, child.getChildNodeId(), uuid, null, strategyMetadata, InitiateMode.CREATE);
        }
        MaxConcurrentChildCallback maxConcurrentChildCallback =
            MaxConcurrentChildCallback.builder()
                .parentNodeExecutionId(nodeExecutionId)
                .ambiance(ambiance)
                .maxConcurrency(maxConcurrency)
                .proceedIfFailed(request.getChildren().getShouldProceedIfFailed())
                .build();

        String waitInstanceId = waitNotifyEngine.waitForAllOn(publisherName, maxConcurrentChildCallback, uuid);
        log.info("SpawnChildrenRequestProcessor registered a waitInstance for maxConcurrency with waitInstanceId: {}",
            waitInstanceId);
        currentChild++;
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

  private boolean shouldCreateAndStart(int maxConcurrency, int currentChild) {
    return currentChild < maxConcurrency;
  }
}
