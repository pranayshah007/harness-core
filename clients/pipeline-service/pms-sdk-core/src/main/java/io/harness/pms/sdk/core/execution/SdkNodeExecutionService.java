/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.events.QueueTaskRequest;
import io.harness.pms.contracts.execution.events.SpawnChildRequest;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.execution.events.SuspendChainRequest;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;

import java.util.Map;
import lombok.NonNull;

@OwnedBy(CDC)
public interface SdkNodeExecutionService {
  void suspendChainExecution(Ambiance ambiance, SuspendChainRequest suspendChainRequest);

  void addExecutableResponse(Ambiance ambiance, ExecutableResponse executableResponse);

  default void handleStepResponse(Ambiance ambiance, @NonNull StepResponseProto stepResponse) {
    handleStepResponse(ambiance, stepResponse, null);
  }

  void handleStepResponse(
      Ambiance ambiance, @NonNull StepResponseProto stepResponse, ExecutableResponse executableResponse);

  void resumeNodeExecution(Ambiance ambiance, Map<String, ResponseData> response, boolean asyncError);

  void handleFacilitationResponse(
      Ambiance ambiance, @NonNull String notifyId, FacilitatorResponseProto facilitatorResponseProto);

  void handleAdviserResponse(Ambiance ambiance, @NonNull String notifyId, AdviserResponse adviserResponse);

  void handleEventError(
      NodeExecutionEventType eventType, Ambiance ambiance, String eventNotifyId, FailureInfo failureInfo);

  void spawnChild(Ambiance ambiance, SpawnChildRequest spawnChildRequest);

  void queueTaskRequest(Ambiance ambiance, QueueTaskRequest queueTaskRequest);

  void spawnChildren(Ambiance ambiance, SpawnChildrenRequest spawnChildrenRequest);

  void handleProgressResponse(Ambiance ambiance, ProgressData progressData);
}
