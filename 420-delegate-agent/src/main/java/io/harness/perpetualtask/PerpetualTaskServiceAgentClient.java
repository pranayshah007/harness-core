/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.rest.CallbackWithRetry;
import io.harness.util.DelegateRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

@Singleton
@Slf4j
public class PerpetualTaskServiceAgentClient {
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  public List<PerpetualTaskAssignDetails> perpetualTaskList(String delegateId, String accountId) {
    try {
      Call<PerpetualTaskListResponse> call = delegateAgentManagerClient.perpetualTaskList(delegateId, accountId);
      PerpetualTaskListResponse response = DelegateRestUtils.executeRestCall(call);
      if (response != null) {
        return response.getPerpetualTaskAssignDetailsList();
      }
    } catch (Exception e) {
      log.error("Error while getting perpetualTaskList ", e);
    }
    return null;
  }

  public PerpetualTaskExecutionContext perpetualTaskContext(PerpetualTaskId taskId, String accountId) {
    try {
      Call<PerpetualTaskContextResponse> perpetualTaskContextResponseCall =
          delegateAgentManagerClient.perpetualTaskContext(taskId.getId(), accountId);
      PerpetualTaskContextResponse response = DelegateRestUtils.executeRestCall(perpetualTaskContextResponseCall);
      if (response != null && response.getPerpetualTaskContext() != null) {
        PerpetualTaskExecutionContext perpetualTaskExecutionContext = response.getPerpetualTaskContext();
        if (!perpetualTaskExecutionContext.hasTaskParams()) {
          log.warn("No Task params for PT task {}", taskId);
        }
        return perpetualTaskExecutionContext;
      } else {
        log.warn("PT Context missing {}", taskId.getId());
      }
    } catch (Exception e) {
      log.error("Error while getting perpetualTaskContext ", e);
    }
    return null;
  }

  public void recordPerpetualTaskFailure(
      PerpetualTaskId taskId, String accountId, PerpetualTaskResponse perpetualTaskResponse) {
    try {
      PerpetualTaskFailureRequest perpetualTaskFailureRequest =
          PerpetualTaskFailureRequest.newBuilder()
              .setId(taskId.getId())
              .setExceptionMessage(perpetualTaskResponse.getResponseMessage())
              .build();

      Call<PerpetualTaskFailureResponse> call =
          delegateAgentManagerClient.recordPerpetualTaskFailure(accountId, perpetualTaskFailureRequest);
      PerpetualTaskFailureResponse perpetualTaskFailureResponse = DelegateRestUtils.executeRestCall(call);
    } catch (IOException ex) {
      log.error(ex.getMessage());
    } catch (Exception e) {
      log.error("Error during recording PT task failure ", e);
    }
  }

  private <T> T executeAsyncCallWithRetry(Call<T> call, CompletableFuture<T> result)
      throws IOException, ExecutionException, InterruptedException {
    call.enqueue(new CallbackWithRetry<T>(call, result));
    return result.get();
  }
}
