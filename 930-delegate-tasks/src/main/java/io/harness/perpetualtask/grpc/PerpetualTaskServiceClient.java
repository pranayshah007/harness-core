/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.grpc;

import io.harness.delegate.DelegateId;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.HeartbeatRequest;
import io.harness.perpetualtask.PerpetualTaskAssignDetails;
import io.harness.perpetualtask.PerpetualTaskContextRequest;
import io.harness.perpetualtask.PerpetualTaskExecutionContext;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskListRequest;
import io.harness.perpetualtask.PerpetualTaskListResponse;
import io.harness.perpetualtask.PerpetualTaskManagerClient;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.PerpetualTaskServiceGrpc.PerpetualTaskServiceBlockingStub;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import io.harness.rest.RestResponse;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;


@Singleton
@Slf4j
public class PerpetualTaskServiceClient {
  private final PerpetualTaskServiceBlockingStub serviceBlockingStub;
  @Inject private PerpetualTaskManagerClient perpetualTaskManagerClient;

  @Inject
  public PerpetualTaskServiceClient(PerpetualTaskServiceBlockingStub perpetualTaskServiceBlockingStub) {
    serviceBlockingStub = perpetualTaskServiceBlockingStub;
  }

  public List<PerpetualTaskAssignDetails> perpetualTaskList(String delegateId) {

    PerpetualTaskListRequest request = PerpetualTaskListRequest.newBuilder()
            .setDelegateId(DelegateId.newBuilder().setId(delegateId).build())
            .build();
    try {
      RestResponse<PerpetualTaskListResponse> restResponse = executeRestCall(perpetualTaskManagerClient.perpetualTaskList(request, ""));
      return restResponse.getResource().getPerpetualTaskAssignDetailsList();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public PerpetualTaskExecutionContext perpetualTaskContext(PerpetualTaskId taskId) {
    PerpetualTaskContextRequest perpetualTaskContextRequest = PerpetualTaskContextRequest.newBuilder().setPerpetualTaskId(taskId).build();
    try {
      RestResponse<PerpetualTaskExecutionContext> restResponse = executeRestCall(perpetualTaskManagerClient.perpetualTaskContext(perpetualTaskContextRequest, ""));
       return restResponse.getResource();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public void heartbeat(PerpetualTaskId taskId, Instant taskStartTime, PerpetualTaskResponse perpetualTaskResponse) {
    try {
     HeartbeatRequest heartbeatRequest = HeartbeatRequest.newBuilder()
              .setId(taskId.getId())
              .setHeartbeatTimestamp(HTimestamps.fromInstant(taskStartTime))
              .setResponseCode(perpetualTaskResponse.getResponseCode())
              .setResponseMessage(perpetualTaskResponse.getResponseMessage())
              .build();
      executeRestCall(perpetualTaskManagerClient.heartbeat(heartbeatRequest, ""));
    } catch (IOException ex) {
      log.error(ex.getMessage());
    }
  }

  private <T> T executeRestCall(Call<T> call) throws IOException {
    Response<T> response = null;
    try {
      response = call.execute();
      return response.body();
    } catch (Exception e) {
      log.error("error executing rest call", e);
      throw e;
    } finally {
      if (response != null && !response.isSuccessful()) {
        String errorResponse = response.errorBody().string();
        log.info("Error responding");
        response.errorBody().close();
      }
    }
  }
}
