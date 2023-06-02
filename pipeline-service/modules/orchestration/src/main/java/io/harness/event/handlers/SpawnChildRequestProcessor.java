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
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.InitiateNodeHelper;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SpawnChildRequest;
import io.harness.pms.execution.utils.SdkResponseEventUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class SpawnChildRequestProcessor implements SdkResponseProcessor {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private InitiateNodeHelper initiateNodeHelper;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    Ambiance ambiance = event.getAmbiance();
    SpawnChildRequest request = event.getSpawnChildRequest();
    String childNodeId = extractChildNodeId(request);
    String childInstanceId = generateUuid();
    initiateNodeHelper.publishEvent(ambiance, childNodeId, childInstanceId);

    // Attach a Callback to the parent for the child
    EngineResumeCallback callback = EngineResumeCallback.builder().ambiance(ambiance).build();
    String waitInstanceId = waitNotifyEngine.waitForAllOn(publisherName, callback, childInstanceId);
    log.info("SpawnChildRequestProcessor registered a waitInstance with id: {}", waitInstanceId);

    // Update the parent with executable response
    nodeExecutionService.updateV2(SdkResponseEventUtils.getNodeExecutionId(event),
        ops -> ops.addToSet(NodeExecutionKeys.executableResponses, buildExecutableResponse(request)));
  }

  private String extractChildNodeId(SpawnChildRequest spawnChildRequest) {
    switch (spawnChildRequest.getSpawnableExecutableResponseCase()) {
      case CHILD:
        return spawnChildRequest.getChild().getChildNodeId();
      case CHILDCHAIN:
        return spawnChildRequest.getChildChain().getNextChildId();
      default:
        throw new InvalidRequestException("CHILD or CHILD_CHAIN response should be set");
    }
  }

  private ExecutableResponse buildExecutableResponse(SpawnChildRequest spawnChildRequest) {
    switch (spawnChildRequest.getSpawnableExecutableResponseCase()) {
      case CHILD:
        return ExecutableResponse.newBuilder().setChild(spawnChildRequest.getChild()).build();
      case CHILDCHAIN:
        return ExecutableResponse.newBuilder().setChildChain(spawnChildRequest.getChildChain()).build();
      default:
        throw new InvalidRequestException("CHILD or CHILD_CHAIN response should be set");
    }
  }
}
