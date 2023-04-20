/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.tasks;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.AccountId;
import io.harness.delegate.CancelTaskRequest;
import io.harness.delegate.CancelTaskResponse;
import io.harness.delegate.DelegateServiceGrpc.DelegateServiceBlockingStub;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.SubmitTaskResponse;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskMode;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.HTimestamps;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest.RequestCase;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.utils.PmsGrpcClientUtils;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.util.Timestamps;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class NgDelegate2TaskExecutor implements TaskExecutor {
  @Inject private DelegateServiceBlockingStub delegateServiceBlockingStub;
  @Inject private DelegateSyncService delegateSyncService;
  @Inject private DelegateAsyncService delegateAsyncService;
  @Inject private Supplier<DelegateCallbackToken> tokenSupplier;

  @Override
  public String queueTask(Map<String, String> setupAbstractions, TaskRequest taskRequest, Duration holdFor) {
    TaskRequestValidityCheck check = validateTaskRequest(taskRequest, TaskMode.ASYNC);
    if (!check.isValid()) {
      throw new InvalidRequestException(check.getMessage());
    }

    SubmitTaskResponse submitTaskResponse =
        PmsGrpcClientUtils.retryAndProcessException(delegateServiceBlockingStub::submitTaskV2,
            buildTaskRequestWithToken(taskRequest.getDelegateTaskRequest().getRequest()));
    delegateAsyncService.setupTimeoutForTask(submitTaskResponse.getTaskId().getId(),
        Timestamps.toMillis(submitTaskResponse.getTotalExpiry()), currentTimeMillis() + holdFor.toMillis());
    return submitTaskResponse.getTaskId().getId();
  }

  @Override
  public <T extends ResponseData> T executeTask(Map<String, String> setupAbstractions, TaskRequest taskRequest) {
    TaskRequestValidityCheck check = validateTaskRequest(taskRequest, TaskMode.SYNC);
    if (!check.isValid()) {
      throw new InvalidRequestException(check.getMessage());
    }
    SubmitTaskRequest submitTaskRequest = buildTaskRequestWithToken(taskRequest.getDelegateTaskRequest().getRequest());
    SubmitTaskResponse submitTaskResponse =
        PmsGrpcClientUtils.retryAndProcessException(delegateServiceBlockingStub::submitTaskV2, submitTaskRequest);
    return delegateSyncService.waitForTask(submitTaskResponse.getTaskId().getId(),
        submitTaskRequest.getDetails().getType().getType(),
        Duration.ofMillis(HTimestamps.toMillis(submitTaskResponse.getTotalExpiry()) - currentTimeMillis()), null);
  }

  private TaskRequestValidityCheck validateTaskRequest(TaskRequest taskRequest, TaskMode validMode) {
    if (taskRequest.getRequestCase() != RequestCase.DELEGATETASKREQUEST) {
      return TaskRequestValidityCheck.builder()
          .valid(false)
          .message("Task Request doesnt contain delegate Task Request")
          .build();
    }
    String message = null;
    SubmitTaskRequest submitTaskRequest = taskRequest.getDelegateTaskRequest().getRequest();
    TaskMode mode = submitTaskRequest.getDetails().getMode();
    boolean valid = mode == validMode;
    if (!valid) {
      message = String.format("DelegateTaskRequest Mode %s Not Supported", mode);
    }
    return TaskRequestValidityCheck.builder().valid(valid).message(message).build();
  }

  @Override
  public void expireTask(Map<String, String> setupAbstractions, String taskId) {
    throw new NotImplementedException("Expire task is not implemented");
  }

  private SubmitTaskRequest buildTaskRequestWithToken(SubmitTaskRequest request) {
    return request.toBuilder().setCallbackToken(tokenSupplier.get()).build();
  }

  @Override
  public boolean abortTask(Map<String, String> setupAbstractions, String taskId) {
    try {
      CancelTaskResponse response = PmsGrpcClientUtils.retryAndProcessException(
          delegateServiceBlockingStub::cancelTaskV2,
          CancelTaskRequest.newBuilder()
              .setAccountId(AccountId.newBuilder().setId(setupAbstractions.get(SetupAbstractionKeys.accountId)).build())
              .setTaskId(TaskId.newBuilder().setId(taskId).build())
              .build());
      return true;
    } catch (Exception ex) {
      log.error("Failed to abort task with taskId: {}, Error : {}", taskId, ex.getMessage());
      return false;
    }
  }

  @Value
  @Builder
  private static class TaskRequestValidityCheck {
    boolean valid;
    String message;
  }
}
