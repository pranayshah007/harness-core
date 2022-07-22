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
import io.harness.exception.DelegateServiceLiteException;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.rest.CallbackWithRetry;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@TargetModule(HarnessModule._920_DELEGATE_AGENT_BEANS)
public class DelegateServiceAgentClient {
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  public boolean sendTaskProgressUpdate(
      AccountId accountId, TaskId taskId, DelegateCallbackToken delegateCallbackToken, byte[] responseData) {
    try {
      Call<SendTaskProgressResponse> call = delegateAgentManagerClient.sendTaskProgressUpdate(
          SendTaskProgressRequest.newBuilder()
              .setAccountId(accountId)
              .setTaskId(taskId)
              .setCallbackToken(delegateCallbackToken)
              .setTaskResponseData(
                  TaskResponseData.newBuilder().setKryoResultsData(ByteString.copyFrom(responseData)).build())
              .build(),
          accountId.getId());
      SendTaskProgressResponse response = executeRestCall(call);
      return response.getSuccess();
    } catch (Exception e) {
      log.error("Error while sending sendTaskProgressUpdate ", e);
    }
    return false;
  }

  public TaskExecutionStage taskProgress(AccountId accountId, TaskId taskId) {
    try {
      Call<TaskProgressResponse> call = delegateAgentManagerClient.taskProgress(
          TaskProgressRequest.newBuilder().setAccountId(accountId).setTaskId(taskId).build(), accountId.getId());
      TaskProgressResponse response = executeRestCall(call);
      return response.getCurrentlyAtStage();
    } catch (Exception ex) {
      throw new DelegateServiceLiteException("Unexpected error occurred while checking task progress.", ex);
    }
  }

  public boolean sendTaskStatus(
      AccountId accountId, TaskId taskId, DelegateCallbackToken delegateCallbackToken, byte[] responseData) {
    try {
      Call<SendTaskStatusResponse> call = delegateAgentManagerClient.sendTaskStatus(
          SendTaskStatusRequest.newBuilder()
              .setAccountId(accountId)
              .setTaskId(taskId)
              .setCallbackToken(delegateCallbackToken)
              .setTaskResponseData(
                  TaskResponseData.newBuilder().setKryoResultsData(ByteString.copyFrom(responseData)).build())
              .build(),
          accountId.getId());
      SendTaskStatusResponse response = executeRestCall(call);
      return response.getSuccess();
    } catch (Exception ex) {
      throw new DelegateServiceLiteException("Unexpected error occurred while sending task status.", ex);
    }
  }

  private <T> T executeAsyncCallWithRetry(Call<T> call, CompletableFuture<T> result)
      throws IOException, ExecutionException, InterruptedException {
    call.enqueue(new CallbackWithRetry<T>(call, result));
    return result.get();
  }

  private <T> T executeRestCall(Call<T> call) throws IOException {
    Response<T> response = null;
    try {
      response = call.execute();
      if (response.isSuccessful()) {
        return response.body();
      }
    } catch (Exception e) {
      log.error("error executing rest call", e);
      throw e;
    } finally {
      if (response != null && !response.isSuccessful()) {
        String errorResponse = response.errorBody().string();
        int errorCode = response.code();
        log.warn("TP:Received Error {} Response: {}", errorCode, errorResponse);

        response.errorBody().close();
      }
    }
    return null;
  }
}
