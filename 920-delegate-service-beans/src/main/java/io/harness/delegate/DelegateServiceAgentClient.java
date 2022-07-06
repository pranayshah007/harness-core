/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.DelegateServiceGrpc.DelegateServiceBlockingStub;
import io.harness.exception.DelegateServiceLiteException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._920_DELEGATE_AGENT_BEANS)
public class DelegateServiceAgentClient {
  private final DelegateServiceBlockingStub delegateServiceBlockingStub;

  @Inject
  public DelegateServiceAgentClient(
      @Named("agent-client-stub") DelegateServiceBlockingStub delegateServiceBlockingStub) {
    this.delegateServiceBlockingStub = delegateServiceBlockingStub;
  }

  public boolean sendTaskProgressUpdate(
      AccountId accountId, TaskId taskId, DelegateCallbackToken delegateCallbackToken, byte[] responseData) {
    try {
      SendTaskProgressResponse response =
          delegateServiceBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS)
              .sendTaskProgress(
                  SendTaskProgressRequest.newBuilder()
                      .setAccountId(accountId)
                      .setTaskId(taskId)
                      .setCallbackToken(delegateCallbackToken)
                      .setTaskResponseData(
                          TaskResponseData.newBuilder().setKryoResultsData(ByteString.copyFrom(responseData)).build())
                      .build());

      return response.getSuccess();
    } catch (StatusRuntimeException ex) {
      throw new DelegateServiceClientException("Unexpected error occurred while sending task progress update.", ex);
    }
  }

  public ExecuteParkedTaskResponse executeParkedTask(AccountId accountId, TaskId taskId) {
    try {
      return delegateServiceBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS)
          .executeParkedTask(ExecuteParkedTaskRequest.newBuilder().setTaskId(taskId).setAccountId(accountId).build());
    } catch (StatusRuntimeException ex) {
      throw new DelegateServiceLiteException("Unexpected error occurred while executing parked task.", ex);
    }
  }

  public FetchParkedTaskStatusResponse fetchParkedTaskStatus(
      AccountId accountId, TaskId taskId, DelegateCallbackToken delegateCallbackToken) {
    try {
      return delegateServiceBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS)
          .fetchParkedTaskStatus(FetchParkedTaskStatusRequest.newBuilder()
                                     .setAccountId(accountId)
                                     .setTaskId(taskId)
                                     .setCallbackToken(delegateCallbackToken)
                                     .build());
    } catch (StatusRuntimeException ex) {
      throw new DelegateServiceLiteException("Unexpected error occurred fetching parked task results.", ex);
    }
  }

  public TaskExecutionStage taskProgress(AccountId accountId, TaskId taskId) {
    try {
      TaskProgressResponse response =
          delegateServiceBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS)
              .taskProgress(TaskProgressRequest.newBuilder().setAccountId(accountId).setTaskId(taskId).build());

      return response.getCurrentlyAtStage();
    } catch (StatusRuntimeException ex) {
      throw new DelegateServiceLiteException("Unexpected error occurred while checking task progress.", ex);
    }
  }

  public boolean sendTaskStatus(
      AccountId accountId, TaskId taskId, DelegateCallbackToken delegateCallbackToken, byte[] responseData) {
    try {
      SendTaskStatusResponse response =
          delegateServiceBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS)
              .sendTaskStatus(
                  SendTaskStatusRequest.newBuilder()
                      .setAccountId(accountId)
                      .setTaskId(taskId)
                      .setCallbackToken(delegateCallbackToken)
                      .setTaskResponseData(
                          TaskResponseData.newBuilder().setKryoResultsData(ByteString.copyFrom(responseData)).build())
                      .build());

      return response.getSuccess();
    } catch (StatusRuntimeException ex) {
      throw new DelegateServiceLiteException("Unexpected error occurred while sending task status.", ex);
    }
  }
}
