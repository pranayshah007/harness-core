/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.auth;

import static io.harness.grpc.auth.DelegateAuthCallCredentials.ACCOUNT_ID_METADATA_KEY;
import static io.harness.grpc.auth.DelegateAuthCallCredentials.TOKEN_METADATA_KEY;
import static io.harness.grpc.auth.DelegateAuthServerInterceptor.ACCOUNT_ID_CTX_KEY;
import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.event.EventPublisherGrpc;
import io.harness.event.PublishMessage;
import io.harness.event.PublishRequest;
import io.harness.event.client.FakeService;
import io.harness.exception.AccessDeniedException;
import io.harness.rule.Owner;
import io.harness.security.DelegateTokenAuthenticator;

import io.grpc.Channel;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.services.HealthStatusManager;
import io.grpc.stub.MetadataUtils;
import io.grpc.testing.GrpcCleanupRule;
import lombok.val;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateAuthServerInterceptorTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String TOKEN = "TOKEN";
  private ContextRecordingInterceptor contextRecordingInterceptor;

  private DelegateTokenAuthenticator tokenAuthenticator;

  private Channel channel;

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
  private FakeService fakeService;

  @Before
  public void setUp() throws Exception {
    tokenAuthenticator = mock(DelegateTokenAuthenticator.class);
    fakeService = new FakeService();
    String serverName = InProcessServerBuilder.generateName();
    contextRecordingInterceptor = new ContextRecordingInterceptor();
    grpcCleanup.register(InProcessServerBuilder.forName(serverName)
                             .directExecutor()
                             .addService(fakeService)
                             .addService(new HealthStatusManager().getHealthService())
                             .intercept(contextRecordingInterceptor)
                             .intercept(new DelegateAuthServerInterceptor(tokenAuthenticator))
                             .build()
                             .start());
    channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPassThroughIfExcludedService() throws Exception {
    val healthStub = HealthGrpc.newBlockingStub(channel);
    assertThatCode(() -> healthStub.check(HealthCheckRequest.newBuilder().build())).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldPassThroughWithoutAccountIdAndWithServiceId() throws Exception {
    val metadata = new Metadata();
    metadata.put(Metadata.Key.of("serviceId", Metadata.ASCII_STRING_MARSHALLER), "service");
    val eventSvcStub = EventPublisherGrpc.newBlockingStub(channel).withInterceptors(
        MetadataUtils.newAttachHeadersInterceptor(metadata));
    assertThatCode(() -> eventSvcStub.publish(PublishRequest.newBuilder().build())).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldBlockIfNoAccountIdInMetadata() throws Exception {
    val eventSvcStub = EventPublisherGrpc.newBlockingStub(channel);
    assertThatExceptionOfType(StatusRuntimeException.class)
        .isThrownBy(() -> eventSvcStub.publish(PublishRequest.newBuilder().build()))
        .withMessage("UNAUTHENTICATED: Account id missing");
    assertThat(fakeService.getMessageCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldBlockIfNoTokenInMetadata() throws Exception {
    val metadata = new Metadata();
    metadata.put(ACCOUNT_ID_METADATA_KEY, ACCOUNT_ID);
    val eventSvcStub = EventPublisherGrpc.newBlockingStub(channel).withInterceptors(
        MetadataUtils.newAttachHeadersInterceptor(metadata));
    assertThatExceptionOfType(StatusRuntimeException.class)
        .isThrownBy(() -> eventSvcStub.publish(PublishRequest.newBuilder().build()))
        .withMessage("UNAUTHENTICATED: Token missing");
    assertThat(fakeService.getMessageCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldBlockIfTokenValidationFails() throws Exception {
    doThrow(new AccessDeniedException("Key not found", null))
        .when(tokenAuthenticator)
        .validateDelegateToken(ACCOUNT_ID, TOKEN, null, null, false);
    val metadata = new Metadata();
    metadata.put(ACCOUNT_ID_METADATA_KEY, ACCOUNT_ID);
    metadata.put(TOKEN_METADATA_KEY, TOKEN);
    val eventSvcStub = EventPublisherGrpc.newBlockingStub(channel).withInterceptors(
        MetadataUtils.newAttachHeadersInterceptor(metadata));
    assertThatExceptionOfType(StatusRuntimeException.class)
        .isThrownBy(() -> eventSvcStub.publish(PublishRequest.newBuilder().build()))
        .withMessage("UNAUTHENTICATED: Key not found");
    assertThat(fakeService.getMessageCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPassAndAddAccountIdToContextIfTokenValidationSucceeds() throws Exception {
    val metadata = new Metadata();
    metadata.put(ACCOUNT_ID_METADATA_KEY, ACCOUNT_ID);
    metadata.put(TOKEN_METADATA_KEY, TOKEN);
    val eventSvcStub = EventPublisherGrpc.newBlockingStub(channel).withInterceptors(
        MetadataUtils.newAttachHeadersInterceptor(metadata));
    eventSvcStub.publish(PublishRequest.newBuilder().addMessages(PublishMessage.newBuilder()).build());
    verify(tokenAuthenticator).validateDelegateToken(ACCOUNT_ID, TOKEN, null, null, false);
    assertThat(ACCOUNT_ID_CTX_KEY.get(contextRecordingInterceptor.lastContext)).isEqualTo(ACCOUNT_ID);
    assertThat(fakeService.getMessageCount()).isEqualTo(1);
  }

  // A server interceptor to spy on the value of the context of the last call.
  static class ContextRecordingInterceptor implements ServerInterceptor {
    volatile Context lastContext;

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
      this.lastContext = Context.current();
      return Contexts.interceptCall(Context.current(), call, headers, next);
    }
  }
}
