/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.auth;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.InterceptorPriority;
import io.harness.grpc.utils.GrpcAuthUtils;
import io.harness.security.DelegateTokenAuthenticator;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ServerInterceptor} that validates the delegate token, and populates context with accountId before calling the
 * rpc implementation on server-side.
 */
@OwnedBy(HarnessTeam.DEL)
@Slf4j
@Singleton
@InterceptorPriority(10)
public class DelegateAuthServerInterceptor implements ServerInterceptor {
  public static final Context.Key<String> ACCOUNT_ID_CTX_KEY = Context.key("accountId");
  private static final ServerCall.Listener NOOP_LISTENER = new ServerCall.Listener() {};
  private static final Set<String> INCLUDED_SERVICES =
      ImmutableSet.of("io.harness.perpetualtask.PerpetualTaskService", "io.harness.event.PingPongService",
          "io.harness.event.EventPublisher", "io.harness.delegate.DelegateService", "io.harness.delegate.DelegateTask");

  private final DelegateTokenAuthenticator tokenAuthenticator;

  @Inject
  public DelegateAuthServerInterceptor(DelegateTokenAuthenticator tokenAuthenticator) {
    this.tokenAuthenticator = tokenAuthenticator;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
    if (excluded(call)) {
      return Contexts.interceptCall(Context.current(), call, metadata, next);
    }

    String accountId = metadata.get(DelegateAuthCallCredentials.ACCOUNT_ID_METADATA_KEY);
    String token = metadata.get(DelegateAuthCallCredentials.TOKEN_METADATA_KEY);
    String delegateId = metadata.get(DelegateAuthCallCredentials.DELEGATE_ID);
    String delegateTokenName = metadata.get(DelegateAuthCallCredentials.DELEGATE_TOKEN_NAME);

    // Urgent fix for DEL-1954. We are allowing delegate service to be invoked by delegate agent, in which case
    // accountId is mandatory, but also by other backend services, in which case serviceId is mandatory. If accountId is
    // present this interceptor should authorize, but if it is not present and serviceId is present, then we need to let
    // ServiceAuthServerInterceptor with lower interceptor priority to authorize.
    String serviceId = GrpcAuthUtils.getServiceIdFromRequest(metadata).orElse(null);
    if (accountId == null && serviceId != null) {
      return Contexts.interceptCall(Context.current(), call, metadata, next);
    }

    @SuppressWarnings("unchecked") Listener<ReqT> noopListener = NOOP_LISTENER;
    if (accountId == null) {
      log.warn("No account id in metadata. Token verification failed");
      call.close(Status.UNAUTHENTICATED.withDescription("Account id missing"), metadata);
      return noopListener;
    }
    if (token == null) {
      log.warn("No token in metadata. Token verification failed");
      call.close(Status.UNAUTHENTICATED.withDescription("Token missing"), metadata);
      return noopListener;
    }
    Context ctx;
    try {
      tokenAuthenticator.validateDelegateToken(accountId, token, delegateId, delegateTokenName, false);
      ctx = GrpcAuthUtils.newAuthenticatedContext().withValue(ACCOUNT_ID_CTX_KEY, accountId);
    } catch (Exception e) {
      log.warn("Token verification failed. Unauthenticated", e);
      call.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e), metadata);
      return noopListener;
    }
    return Contexts.interceptCall(ctx, call, metadata, next);
  }

  private <RespT, ReqT> boolean excluded(ServerCall<ReqT, RespT> call) {
    return !INCLUDED_SERVICES.contains(call.getMethodDescriptor().getServiceName());
  }
}
