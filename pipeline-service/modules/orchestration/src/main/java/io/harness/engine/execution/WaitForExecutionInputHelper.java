/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.ExecutionInputInstance;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class WaitForExecutionInputHelper {
  private static final Long MILLIS_IN_SIX_MONTHS = 86400 * 30 * 6L;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private ExecutionInputService executionInputService;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;
  public void waitForExecutionInput(Ambiance ambiance, String nodeExecutionId, String executionInputTemplate) {
    Long currentTime = System.currentTimeMillis();
    String inputInstanceId = UUIDGenerator.generateUuid();
    WaitForExecutionInputCallback waitForExecutionInputCallback = WaitForExecutionInputCallback.builder()
                                                                      .nodeExecutionId(nodeExecutionId)
                                                                      .ambiance(ambiance)
                                                                      .inputInstanceId(inputInstanceId)
                                                                      .build();
    waitNotifyEngine.waitForAllOn(publisherName, waitForExecutionInputCallback, inputInstanceId);
    executionInputService.save(ExecutionInputInstance.builder()
                                   .inputInstanceId(inputInstanceId)
                                   .nodeExecutionId(nodeExecutionId)
                                   .template(executionInputTemplate)
                                   .createdAt(currentTime)
                                   .validUntil(currentTime + MILLIS_IN_SIX_MONTHS)
                                   .build());
    // Updating the current node status. InputWaitingStatusUpdateHandler will update status of parent recursively.
    nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.INPUT_WAITING, null, EnumSet.noneOf(Status.class));
  }
}
