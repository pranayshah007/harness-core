/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.pingpong;

import static io.harness.grpc.auth.DelegateAuthServerInterceptor.ACCOUNT_ID_CTX_KEY;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.event.Ping;
import io.harness.event.PingPongServiceGrpc.PingPongServiceImplBase;
import io.harness.event.Pong;
import io.harness.grpc.utils.HTimestamps;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.service.impl.DelegateDao;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@Slf4j
public class PingPongService extends PingPongServiceImplBase {
  private final DelegateDao delegateDao;

  @Inject
  public PingPongService(DelegateDao delegateDao) {
    this.delegateDao = delegateDao;
  }

  @Override
  public void tryPing(Ping ping, StreamObserver<Pong> responseObserver) {
    try (AutoLogContext ignore1 = new AccountLogContext(ACCOUNT_ID_CTX_KEY.get(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(ping.getDelegateId(), OVERRIDE_ERROR)) {
      log.info("Ping at {} received from delegateId {} with processId: {}, version: {}",
          HTimestamps.toInstant(ping.getPingTimestamp()), ping.getDelegateId(), ping.getProcessId(), ping.getVersion());
      responseObserver.onNext(Pong.newBuilder().build());
      responseObserver.onCompleted();
    }
  }
}
