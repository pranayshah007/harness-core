/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.grpc.utils.HTimestamps;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.rest.CallbackWithRetry;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
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
    CompletableFuture<PerpetualTaskListResponse> result = new CompletableFuture<>();
    try {
      Call<PerpetualTaskListResponse> call = delegateAgentManagerClient.perpetualTaskList(delegateId, accountId);
      executeAsyncCallWithRetry(call, result);
      return result.get().getPerpetualTaskAssignDetailsList();

    } catch (Exception e) {
      log.error("Error while getting perpetualTaskList ", e);
    }
    return null;
  }

  public PerpetualTaskExecutionContext perpetualTaskContext(PerpetualTaskId taskId, String accountId) {
    CompletableFuture<PerpetualTaskContextResponse> result = new CompletableFuture<>();
    try {
      Call<PerpetualTaskContextResponse> perpetualTaskContextResponseCall =
          delegateAgentManagerClient.perpetualTaskContext(taskId.getId(), accountId);
      executeAsyncCallWithRetry(perpetualTaskContextResponseCall, result);
      PerpetualTaskExecutionContext perpetualTaskExecutionContext = result.get().getPerpetualTaskContext();
      log.info("PT Context params: {}", perpetualTaskExecutionContext);
      return perpetualTaskExecutionContext;
    } catch (InterruptedException | ExecutionException | IOException e) {
      log.error("Error while getting perpetualTaskContext ", e);
    }
    return null;
  }

  public void heartbeat(
      PerpetualTaskId taskId, Instant taskStartTime, PerpetualTaskResponse perpetualTaskResponse, String accountId) {
    CompletableFuture<HeartbeatResponse> result = new CompletableFuture<>();
    try {
      HeartbeatRequest heartbeatRequest = HeartbeatRequest.newBuilder()
                                              .setId(taskId.getId())
                                              .setHeartbeatTimestamp(HTimestamps.fromInstant(taskStartTime))
                                              .setResponseCode(perpetualTaskResponse.getResponseCode())
                                              .setResponseMessage(perpetualTaskResponse.getResponseMessage())
                                              .build();
      if (isEmpty(accountId)) {
        log.warn("Account id is null while sending heartbeat");
      }
      Call<HeartbeatResponse> call = delegateAgentManagerClient.heartbeat(accountId, heartbeatRequest);
      executeAsyncCallWithRetry(call, result);
    } catch (IOException ex) {
      log.error(ex.getMessage());
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error on PT heartbeat ", e);
    }
  }

  private <T> T executeAsyncCallWithRetry(Call<T> call, CompletableFuture<T> result)
      throws IOException, ExecutionException, InterruptedException {
    call.enqueue(new CallbackWithRetry<T>(call, result));
    return result.get();
  }
}
