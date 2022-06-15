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
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.PerpetualTaskServiceGrpc.PerpetualTaskServiceBlockingStub;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class PerpetualTaskServiceGrpcClient {
  private final PerpetualTaskServiceBlockingStub serviceBlockingStub;

  @Inject
  public PerpetualTaskServiceGrpcClient(PerpetualTaskServiceBlockingStub perpetualTaskServiceBlockingStub) {
    serviceBlockingStub = perpetualTaskServiceBlockingStub;
  }

  public List<PerpetualTaskAssignDetails> perpetualTaskList(String delegateId) {
    PerpetualTaskListResponse response =
        serviceBlockingStub.withDeadlineAfter(60, TimeUnit.SECONDS)
            .perpetualTaskList(PerpetualTaskListRequest.newBuilder()
                                   .setDelegateId(DelegateId.newBuilder().setId(delegateId).build())
                                   .build());
    return response.getPerpetualTaskAssignDetailsList();
  }

  public PerpetualTaskExecutionContext perpetualTaskContext(PerpetualTaskId taskId) {
    return serviceBlockingStub.withDeadlineAfter(90, TimeUnit.SECONDS)
        .perpetualTaskContext(PerpetualTaskContextRequest.newBuilder().setPerpetualTaskId(taskId).build())
        .getPerpetualTaskContext();
  }

  public void heartbeat(PerpetualTaskId taskId, Instant taskStartTime, PerpetualTaskResponse perpetualTaskResponse) {
    try {
      serviceBlockingStub.withDeadlineAfter(60, TimeUnit.SECONDS)
          .heartbeat(HeartbeatRequest.newBuilder()
                         .setId(taskId.getId())
                         .setHeartbeatTimestamp(HTimestamps.fromInstant(taskStartTime))
                         .setResponseCode(perpetualTaskResponse.getResponseCode())
                         .setResponseMessage(perpetualTaskResponse.getResponseMessage())
                         .build());
    } catch (StatusRuntimeException ex) {
      log.error("StatusRunTimeException: {}", ex.getMessage());
    }
  }
}
