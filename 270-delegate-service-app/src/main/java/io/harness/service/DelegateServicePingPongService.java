/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessModule._420_DELEGATE_SERVICE;
import static io.harness.grpc.auth.DelegateAuthServerInterceptor.ACCOUNT_ID_CTX_KEY;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.grpc.utils.HTimestamps;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.pingpong.DelegateServicePingPongGrpc;
import io.harness.pingpong.PingDelegateService;
import io.harness.pingpong.PongDelegateService;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(_420_DELEGATE_SERVICE)
public class DelegateServicePingPongService extends DelegateServicePingPongGrpc.DelegateServicePingPongImplBase {
  @Override
  public void tryPing(PingDelegateService ping, StreamObserver<PongDelegateService> responseObserver) {
    try (AutoLogContext ignore1 = new AccountLogContext(ACCOUNT_ID_CTX_KEY.get(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(ping.getDelegateId(), OVERRIDE_ERROR)) {
      log.info("Ping at {} received from delegate with processId: {}, version: {}",
          HTimestamps.toInstant(ping.getPingTimestamp()), ping.getProcessId(), ping.getVersion());
      responseObserver.onNext(PongDelegateService.newBuilder().build());
      responseObserver.onCompleted();
    }
  }
}
