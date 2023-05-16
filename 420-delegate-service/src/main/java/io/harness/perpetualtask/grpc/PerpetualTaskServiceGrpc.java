/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.grpc;

import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.HeartbeatRequest;
import io.harness.perpetualtask.HeartbeatResponse;
import io.harness.perpetualtask.PerpetualTaskAssignDetails;
import io.harness.perpetualtask.PerpetualTaskContextRequest;
import io.harness.perpetualtask.PerpetualTaskContextResponse;
import io.harness.perpetualtask.PerpetualTaskListRequest;
import io.harness.perpetualtask.PerpetualTaskListResponse;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.PerpetualTaskService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class PerpetualTaskServiceGrpc
    extends io.harness.perpetualtask.PerpetualTaskServiceGrpc.PerpetualTaskServiceImplBase {
  @Inject private PerpetualTaskService perpetualTaskService;
  @Override
  public void perpetualTaskList(
      PerpetualTaskListRequest request, StreamObserver<PerpetualTaskListResponse> responseObserver) {
    log.info("perpetualTaskList invoked");
    Instant start = Instant.now();
    List<PerpetualTaskAssignDetails> perpetualTaskAssignDetails =
        perpetualTaskService.listAssignedTasks(request.getDelegateId().getId());
    PerpetualTaskListResponse response =
        PerpetualTaskListResponse.newBuilder().addAllPerpetualTaskAssignDetails(perpetualTaskAssignDetails).build();
    Instant end = Instant.now();
    Duration duration = Duration.between(start, end);
    log.info("perpetualTaskList duration:{}", duration);
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void perpetualTaskContext(
      PerpetualTaskContextRequest request, StreamObserver<PerpetualTaskContextResponse> responseObserver) {
    try {
      responseObserver.onNext(
          PerpetualTaskContextResponse.newBuilder()
              .setPerpetualTaskContext(perpetualTaskService.perpetualTaskContext(request.getPerpetualTaskId().getId()))
              .build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      log.error("Unexpected error occurred while getting perpetual task context.", e);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
    PerpetualTaskResponse perpetualTaskResponse = PerpetualTaskResponse.builder()
                                                      .responseMessage(request.getResponseMessage())
                                                      .responseCode(request.getResponseCode())
                                                      .build();

    long heartbeatMillis = HTimestamps.toInstant(request.getHeartbeatTimestamp()).toEpochMilli();
    perpetualTaskService.triggerCallback(request.getId(), heartbeatMillis, perpetualTaskResponse);
    responseObserver.onNext(HeartbeatResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}
