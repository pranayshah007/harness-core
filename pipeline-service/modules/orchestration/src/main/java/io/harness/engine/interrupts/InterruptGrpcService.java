/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.service.InterruptProtoServiceGrpc.InterruptProtoServiceImplBase;
import io.harness.pms.contracts.service.InterruptRequest;
import io.harness.pms.contracts.service.InterruptResponse;
import io.harness.tasks.BinaryResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class InterruptGrpcService extends InterruptProtoServiceImplBase {
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public void handleAbort(InterruptRequest request, StreamObserver<InterruptResponse> responseObserver) {
    log.info("InterruptGrpcService#handleAbort reached.");
    // adding a dummy response data object
    waitNotifyEngine.doneWith(request.getNotifyId(), BinaryResponseData.builder().build());
    responseObserver.onNext(InterruptResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void handleFailure(InterruptRequest request, StreamObserver<InterruptResponse> responseObserver) {
    log.info("InterruptGrpcService#handleFailureInterrupt reached.");
    // adding a dummy response data object
    waitNotifyEngine.doneWith(request.getNotifyId(), BinaryResponseData.builder().build());
    responseObserver.onNext(InterruptResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  // TODO Combine all 3 methods into one
  @Override
  public void handleExpire(InterruptRequest request, StreamObserver<InterruptResponse> responseObserver) {
    log.info("InterruptGrpcService#handleExpireInterrupt reached.");
    // adding a dummy response data object
    waitNotifyEngine.doneWith(request.getNotifyId(), BinaryResponseData.builder().build());
    responseObserver.onNext(InterruptResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}
