/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.concurrency.MaxConcurrentChildCallback;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.execution.InitiateNodeHelper;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.logging.AutoLogContext;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
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
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    SpawnChildrenRequest request = event.getSpawnChildrenRequest();
    Ambiance ambiance = event.getAmbiance();
    boolean isMatrixFeatureEnabled =
        pmsFeatureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.PIPELINE_MATRIX);
    String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      List<String> callbackIds = new ArrayList<>();
      int currentChild = 0;
      int maxConcurrency = (int) request.getChildren().getMaxConcurrency();
      for (Child child : request.getChildren().getChildrenList()) {
        String uuid = generateUuid();
        callbackIds.add(uuid);
        if (isMatrixFeatureEnabled) {
          InitiateMode initiateMode = InitiateMode.CREATE;
          if (shouldCreateAndStart(maxConcurrency, currentChild)) {
            initiateMode = InitiateMode.CREATE_AND_START;
          }
          createAndStart(ambiance, nodeExecutionId, uuid, child.getChildNodeId(), child.getStrategyMetadata(),
              maxConcurrency, initiateMode);
          currentChild++;
        } else {
          initiateNodeHelper.publishEvent(ambiance, child.getChildNodeId(), uuid);
        }
      }

      if (isMatrixFeatureEnabled) {
        // Save the ConcurrentChildInstance in db
        nodeExecutionInfoService.addConcurrentChildInformation(
            ConcurrentChildInstance.builder()
                .childrenNodeExecutionIds(callbackIds)
                .cursor((int) request.getChildren().getMaxConcurrency())
                .build(),
            nodeExecutionId);
      }

      // Attach a Callback to the parent for the child
      EngineResumeCallback callback = EngineResumeCallback.builder().ambiance(ambiance).build();
      waitNotifyEngine.waitForAllOn(publisherName, callback, callbackIds.toArray(new String[0]));

      // Update the parent with executable response
      nodeExecutionService.updateV2(nodeExecutionId,
          ops
          -> ops.addToSet(NodeExecutionKeys.executableResponses,
              ExecutableResponse.newBuilder().setChildren(request.getChildren()).build()));
    }
  }

  private boolean shouldCreateAndStart(int maxConcurrency, int currentChild) {
    return maxConcurrency == 0 || currentChild < maxConcurrency;
  }

  private void createAndStart(Ambiance ambiance, String parentNodeExecutionId, String childNodeExecutionId,
      String childNodeId, StrategyMetadata strategyMetadata, int maxConcurrency, InitiateMode initiateMode) {
    initiateNodeHelper.publishEvent(ambiance, childNodeId, childNodeExecutionId, strategyMetadata, initiateMode);
    MaxConcurrentChildCallback maxConcurrentChildCallback = MaxConcurrentChildCallback.builder()
                                                                .parentNodeExecutionId(parentNodeExecutionId)
                                                                .ambiance(ambiance)
                                                                .maxConcurrency(maxConcurrency)
                                                                .build();
    waitNotifyEngine.waitForAllOn(publisherName, maxConcurrentChildCallback, childNodeExecutionId);
  }
}
