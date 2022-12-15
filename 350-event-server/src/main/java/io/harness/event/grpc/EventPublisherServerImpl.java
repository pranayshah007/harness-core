/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.grpc;

import static io.harness.grpc.IdentifierKeys.DELEGATE_ID;
import static io.harness.grpc.auth.DelegateAuthServerInterceptor.ACCOUNT_ID_CTX_KEY;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.util.Objects.requireNonNull;

import io.harness.delegate.task.DelegateLogContext;
import io.harness.event.EventPublisherGrpc;
import io.harness.event.PublishRequest;
import io.harness.event.PublishResponse;
import io.harness.event.service.intfc.EventPublisherService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class EventPublisherServerImpl extends EventPublisherGrpc.EventPublisherImplBase {
  private final EventPublisherService eventPublisherService;

  @Inject
  public EventPublisherServerImpl(EventPublisherService eventPublisherService) {
    this.eventPublisherService = eventPublisherService;
  }

  @Override
  public void publish(PublishRequest request, StreamObserver<PublishResponse> responseObserver) {
    String accountId = requireNonNull(ACCOUNT_ID_CTX_KEY.get(Context.current()));
    log.info(
        "Received publish request with {} messages via grpc for accountId: {}", request.getMessagesCount(), accountId);
    String delegateId = request.getMessages(0).getAttributesMap().getOrDefault(DELEGATE_ID, "");
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore1 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      try {
        eventPublisherService.publish(accountId, delegateId, request.getMessagesList(), request.getMessagesCount());
        responseObserver.onNext(PublishResponse.newBuilder().build());
        responseObserver.onCompleted();
      } catch (Exception e) {
        log.error("Exception in Event Publisher Service", e);
        responseObserver.onError(Status.INTERNAL.withCause(e).asException());
      }
    }
  }
}